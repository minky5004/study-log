package com.minky.studylog.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.CategoryRepository;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 가져오기는 남의 파일을 받는 경로라 실패가 정상 흐름의 일부다. 한 건이 배치를 죽이지 않는지,
 * 이미 있는 기록을 덮지 않는지가 이 파일의 관심사다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarkdownImportServiceTest {

    @Autowired MarkdownImportService importService;
    @Autowired StudyLogService studyLogService;
    @Autowired StudyLogRepository studyLogRepository;
    @Autowired CategoryRepository categoryRepository;

    @Test
    @DisplayName("마크다운 하나가 기록 하나로 — 태그는 소문자 정규화 · 소요 시간은 시각에서 재계산")
    void importsSingleNote() {
        ImportReport report = importService.importFrom(List.of(md("a.md", """
                ---
                title: "트랜잭션 격리 수준"
                date: 2026-08-03
                start: "23:00"
                end: "01:00"
                durationMinutes: 9999
                category: "Spring"
                tags: ["JPA", "트랜잭션"]
                summary: "격리 수준 정리"
                ---
                # 노트""")));

        assertThat(report.succeeded()).isEqualTo(1);
        StudyLog saved = studyLogRepository.findAll().getFirst();
        assertThat(saved.getTitle()).isEqualTo("트랜잭션 격리 수준");
        assertThat(saved.getStudyDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        // 파일의 durationMinutes 를 믿었으면 9999 가 들어왔다
        assertThat(saved.getDurationMinutes()).isEqualTo(120);
        assertThat(saved.getCategory().getName()).isEqualTo("Spring");
        assertThat(saved.getTags()).containsExactly("jpa", "트랜잭션");
        assertThat(saved.getSummary()).isEqualTo("격리 수준 정리");
        assertThat(saved.getNote()).isEqualTo("# 노트");
    }

    @Test
    @DisplayName("같은 날짜 · 제목 · 시작 시각이면 덮어쓰지 않고 건너뜀")
    void skipsDuplicates() {
        seed("JPA 기초", LocalDate.of(2026, 8, 3));

        ImportReport report = importService.importFrom(List.of(md("a.md", note("JPA 기초"))));

        assertThat(report.succeeded()).isZero();
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(studyLogRepository.count()).isEqualTo(1);
    }

    /** 대소문자만 다른 제목도 같은 기록으로 본다 — 표기 흔들림으로 사본이 쌓이면 통계가 갈라진다. */
    @Test
    @DisplayName("제목 대소문자만 달라도 중복")
    void skipsDuplicatesIgnoringCase() {
        seed("JPA 기초", LocalDate.of(2026, 8, 3));

        assertThat(importService.importFrom(List.of(md("a.md", note("jpa 기초")))).skipped())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("깨진 파일은 실패로 집계하고 사유를 남김 · 나머지는 계속 처리")
    void collectsFailuresAndContinues() {
        ImportReport report = importService.importFrom(List.of(
                md("a.md", "# 프론트매터 없는 노트"), md("b.md", note("정상"))));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.fileName()).isEqualTo("a.md");
            assertThat(failure.reason()).contains("프론트매터 없음");
        });
    }

    @Test
    @DisplayName("한 배치 안의 동일 날짜 · 제목 · 시각 중복도 두 번째부터 건너뜀")
    void skipsDuplicatesWithinBatch() {
        ImportReport report = importService.importFrom(List.of(
                md("a.md", note("같은 제목")), md("b.md", note("같은 제목"))));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
    }

    /**
     * 하루에 같은 주제를 두 번 앉는 것은 정상 사용이고, 내보내기도 그 둘을 {@code HHmm} 접미사로
     * 갈라 별개 파일로 낸다. 시각이 키에서 빠지면 백업을 되돌릴 때 그 둘째 건이 조용히 빈다.
     */
    @Test
    @DisplayName("날짜 · 제목이 같아도 시작 시각이 다르면 새 기록")
    void keepsSameTitleAtDifferentStartTime() {
        seed("JPA 기초", LocalDate.of(2026, 8, 3));

        ImportReport report = importService.importFrom(
                List.of(md("a.md", note("JPA 기초", "14:00", "15:00"))));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.skipped()).isZero();
        assertThat(studyLogRepository.count()).isEqualTo(2);
    }

    /**
     * 내보내기는 시작 시각까지 같은 기록을 {@code -HHmm-2} 로 한 번 더 가른다
     * ({@code ExportFileNameResolver}). 종료가 키에서 빠지면 그 둘째 파일이 돌아오지 못한다.
     */
    @Test
    @DisplayName("시작 시각이 같아도 종료가 다르면 새 기록")
    void keepsSameStartWithDifferentEndTime() {
        seed("JPA 기초", LocalDate.of(2026, 8, 3));

        ImportReport report = importService.importFrom(
                List.of(md("a.md", note("JPA 기초", "09:00", "12:00"))));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(studyLogRepository.count()).isEqualTo(2);
    }

    /** 배치 안 키와 DB 판정 키가 어긋나면 파일 사이 중복과 DB 중복의 기준이 갈린다. */
    @Test
    @DisplayName("한 배치 안에서도 시작 시각이 다르면 둘 다 저장")
    void keepsSameTitleAtDifferentStartTimeWithinBatch() {
        ImportReport report = importService.importFrom(List.of(
                md("a.md", note("같은 제목")), md("b.md", note("같은 제목", "14:00", "15:00"))));

        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.skipped()).isZero();
    }

    @Test
    @DisplayName("ZIP 업로드는 안의 md 만 골라 처리")
    void readsZipEntries() {
        ImportReport report = importService.importFrom(List.of(zip("export.zip",
                entry("logs/첫째.md", note("첫째")),
                entry("logs/", ""),
                entry("__MACOSX/._첫째.md", note("맥 메타데이터")),
                entry("logs/그림.png", "not markdown"),
                entry("logs/둘째.md", note("둘째")))));

        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.failures()).isEmpty();
        assertThat(studyLogRepository.findAll()).extracting(StudyLog::getTitle)
                .containsExactlyInAnyOrder("첫째", "둘째");
    }

    @Test
    @DisplayName("빈 ZIP · 빈 목록도 오류 없이 0건")
    void handlesEmptyInput() {
        assertThat(importService.importFrom(List.of()).succeeded()).isZero();
        assertThat(importService.importFrom(List.of(zip("empty.zip"))).succeeded()).isZero();
    }

    /**
     * 컬럼 길이를 넘긴 값을 그대로 저장하면 실패 사유가 JDBC 원문
     * ({@code Value too long for column "TITLE"})이 된다 — 화면에 그대로 나가는 문장이라
     * 사용자가 무엇을 고칠지 알 수 있어야 한다.
     */
    @Test
    @DisplayName("컬럼 길이를 넘기면 고칠 수 있는 사유로 실패")
    void reportsReadableReasonForTooLongValues() {
        ImportReport report = importService.importFrom(List.of(
                md("long.md", note("가".repeat(300)))));

        assertThat(report.failures()).singleElement().satisfies(failure ->
                assertThat(failure.reason()).contains("제목이 200자를 넘음", "300자"));
    }

    /**
     * 분야 해석과 기록 저장이 한 트랜잭션에 없으면, 저장이 막힌 뒤에도 분야 행만 영구히 남는다.
     * 분야 관리 화면을 만들지 않기로 했으므로 지울 수단도 없고, 자동완성과 색 배정에 계속 낀다.
     */
    @Test
    @DisplayName("저장이 막히면 그 파일이 만든 분야도 남지 않음")
    void rollsBackCategoryWhenSaveFails() {
        importService.importFrom(List.of(md("long.md",
                note("가".repeat(300)).replace("\"CS\"", "\"오직 이 파일만 쓰는 분야\""))));

        assertThat(categoryRepository.findByNameKey("오직 이 파일만 쓰는 분야")).isEmpty();
    }

    /**
     * 내용을 꺼내는 일 자체가 실패해도 배치는 이어져야 한다. 읽기가 try 밖에 있으면 그 파일에서
     * 배치가 끝나고, 이미 커밋된 앞 건들은 보고 없이 사라진 것처럼 보인다.
     */
    @Test
    @DisplayName("읽지 못한 파일도 실패 한 줄 · 뒤 파일은 계속 처리")
    void unreadableFileDoesNotStopBatch() {
        ImportReport report = importService.importFrom(List.of(
                unreadable("broken-stream.md"), md("b.md", note("정상"))));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failures()).singleElement().satisfies(failure ->
                assertThat(failure.fileName()).isEqualTo("broken-stream.md"));
    }

    private static MultipartFile unreadable(String name) {
        return new MockMultipartFile("files", name, "text/markdown", new byte[] {1}) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("디스크 오류");
            }
        };
    }

    private static String note(String title) {
        return note(title, "09:00", "10:00");
    }

    /** 시작 시각이 중복 판정 키의 일부라 케이스마다 달리 준다. */
    private static String note(String title, String start, String end) {
        return "---\ntitle: \"" + title + "\"\ndate: 2026-08-03\n"
                + "start: \"" + start + "\"\nend: \"" + end + "\"\ncategory: \"CS\"\n---\n본문";
    }

    private void seed(String title, LocalDate date) {
        StudyLogForm form = new StudyLogForm();
        form.setTitle(title);
        form.setStudyDate(date);
        form.setStartTime(LocalTime.of(9, 0));
        form.setEndTime(LocalTime.of(10, 0));
        form.setCategoryName("CS");
        studyLogService.create(form);
    }

    private static MultipartFile md(String name, String content) {
        return new MockMultipartFile("files", name, "text/markdown",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static MultipartFile zip(String name, Entry... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.path()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new MockMultipartFile("files", name, "application/zip", out.toByteArray());
    }

    private static Entry entry(String path, String content) {
        return new Entry(path, content);
    }

    private record Entry(String path, String content) {
    }
}
