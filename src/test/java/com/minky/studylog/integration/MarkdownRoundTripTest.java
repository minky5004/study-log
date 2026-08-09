package com.minky.studylog.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.repository.CategoryRepository;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.service.export.MarkdownExportService;
import com.minky.studylog.service.importer.ImportReport;
import com.minky.studylog.service.importer.MarkdownImportService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 백업이 복구 가능한지를 증명하는 것은 이 테스트뿐이다. 내보내기와 가져오기는 각자의 단위
 * 테스트를 통과해도 형식이 한 칸 어긋나면 왕복에서만 드러난다.
 * <p>
 * <b>{@code @Transactional} 을 붙이지 않는다.</b> {@code NoteWriter} 의 트랜잭션은 전파가
 * 기본값이라 테스트 트랜잭션이 있으면 거기에 합류하고, 그러면 노트 하나가 트랜잭션 하나라는
 * 가져오기의 전제가 시험 대상에서 빠진다. 정리는 {@code @AfterEach} 가 맡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MarkdownRoundTripTest {

    @Autowired MarkdownExportService exportService;
    @Autowired MarkdownImportService importService;
    @Autowired StudyLogService studyLogService;
    @Autowired StudyLogRepository studyLogRepository;
    @Autowired CategoryRepository categoryRepository;

    private TransactionTemplate transactionTemplate;

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void clearDatabase() {
        studyLogRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("내보낸 ZIP 을 비운 DB 에 다시 넣으면 원래 기록과 같아짐")
    void roundTripPreservesEverything() throws IOException {
        seedAll();
        List<Snapshot> before = snapshots();
        byte[] zip = exportedZip();

        studyLogRepository.deleteAll();
        categoryRepository.deleteAll();
        ImportReport report = importService.importFrom(List.of(upload(zip)));

        assertThat(report.failures()).isEmpty();
        assertThat(report.skipped()).isZero();
        assertThat(report.succeeded()).isEqualTo(5);
        assertThat(snapshots()).isEqualTo(before);
    }

    /**
     * 중복 판정 키가 넓어진 뒤에도 같은 파일이 기록을 늘리지 않는지는 이 테스트 하나로만 남는다.
     * 같은 ZIP 은 시각까지 같으므로 전건이 걸려야 한다.
     */
    @Test
    @DisplayName("같은 ZIP 을 다시 올리면 전건 건너뜀")
    void reimportAddsNothing() throws IOException {
        seedAll();
        byte[] zip = exportedZip();

        ImportReport report = importService.importFrom(List.of(upload(zip)));

        assertThat(report.succeeded()).isZero();
        assertThat(report.skipped()).isEqualTo(5);
        assertThat(studyLogRepository.count()).isEqualTo(5);
    }

    /** 형식이 깨지기 쉬운 자리를 일부러 섞는다 — 자정 넘김 · 따옴표와 슬래시 · 본문 구분선. */
    private void seedAll() {
        // 태그를 알파벳 역순으로 넣는다 — 정렬된 순서를 시드로 쓰면 내보내기가 다시 정렬해도
        // 왕복이 통과해, 순서를 잰다고 적어 놓고 아무것도 재지 않는다
        seed("트랜잭션 격리 수준", "2026-08-03", "23:00", "01:00", "Spring",
                "트랜잭션, jpa", "격리 수준 정리", "# 노트\n\n---\n\n구분선 포함 본문");
        seed("따옴표 \"제목\" · 슬래시/포함", "2026-08-03", "09:00", "10:00", "CS",
                null, null, null);
        // 셋과 넷은 날짜·제목이 같고 시각만 다르다 — 내보내기가 HHmm 접미사로 가르는 자리
        seed("같은 날 같은 제목", "2026-08-04", "09:00", "10:00", "Java", "컬렉션", "a", "b");
        seed("같은 날 같은 제목", "2026-08-04", "14:00", "15:00", "Java", "스트림", "c", "d");
        // 다섯은 셋과 시작까지 같고 종료만 다르다 — 접미사가 -0900-2 로 한 번 더 갈리는 자리
        seed("같은 날 같은 제목", "2026-08-04", "09:00", "12:00", "Java", "제네릭", "e", "f");
    }

    private void seed(String title, String date, String start, String end, String category,
                      String tagsCsv, String summary, String note) {
        StudyLogForm form = new StudyLogForm();
        form.setTitle(title);
        form.setStudyDate(LocalDate.parse(date));
        form.setStartTime(LocalTime.parse(start));
        form.setEndTime(LocalTime.parse(end));
        form.setCategoryName(category);
        form.setTagsCsv(tagsCsv);
        form.setSummary(summary);
        form.setNote(note);
        studyLogService.create(form);
    }

    private byte[] exportedZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.writeZip(out);
        return out.toByteArray();
    }

    private static MultipartFile upload(byte[] zip) {
        return new MockMultipartFile("files", "export.zip", "application/zip", zip);
    }

    /**
     * 태그와 분야는 지연 로딩이라 트랜잭션 안에서 값을 떠 온다. 비교는 레코드끼리 — 필드를 하나
     * 늘리면 대조도 함께 늘어야 왕복이 계속 전부를 덮는다.
     */
    private List<Snapshot> snapshots() {
        return transactionTemplate.execute(status ->
                studyLogRepository.findAll(Sort.by("studyDate", "startTime", "endTime")).stream()
                        .map(log -> new Snapshot(
                                log.getTitle(),
                                log.getStudyDate(),
                                log.getStartTime(),
                                log.getEndTime(),
                                log.getDurationMinutes(),
                                log.getCategory().getName(),
                                List.copyOf(log.getTags()),
                                log.getSummary(),
                                log.getNote()))
                        .toList());
    }

    /** 태그를 {@code List} 로 견주는 것이 왕복이 순서까지 덮는다는 뜻이다. */
    private record Snapshot(String title, LocalDate date, LocalTime start, LocalTime end,
                            int durationMinutes, String category, List<String> tags,
                            String summary, String note) {
    }
}
