package com.minky.studylog.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.io.ByteArrayOutputStream;
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
        assertThat(saved.getTags()).containsExactlyInAnyOrder("jpa", "트랜잭션");
        assertThat(saved.getSummary()).isEqualTo("격리 수준 정리");
        assertThat(saved.getNote()).isEqualTo("# 노트");
    }

    @Test
    @DisplayName("같은 날짜 · 같은 제목이 있으면 덮어쓰지 않고 건너뜀")
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
    @DisplayName("한 배치 안의 동일 날짜 · 제목 중복도 두 번째부터 건너뜀")
    void skipsDuplicatesWithinBatch() {
        ImportReport report = importService.importFrom(List.of(
                md("a.md", note("같은 제목")), md("b.md", note("같은 제목"))));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
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

    /**
     * 내보낸 ZIP 을 그대로 되먹이면 전건이 중복이어야 한다. 이 한 줄이 파일명·형식·중복 판정이
     * 서로 맞물려 있다는 증거다.
     */
    @Test
    @DisplayName("빈 ZIP · 빈 목록도 오류 없이 0건")
    void handlesEmptyInput() {
        assertThat(importService.importFrom(List.of()).succeeded()).isZero();
        assertThat(importService.importFrom(List.of(zip("empty.zip"))).succeeded()).isZero();
    }

    private static String note(String title) {
        return "---\ntitle: \"" + title + "\"\ndate: 2026-08-03\n"
                + "start: \"09:00\"\nend: \"10:00\"\ncategory: \"CS\"\n---\n본문";
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
