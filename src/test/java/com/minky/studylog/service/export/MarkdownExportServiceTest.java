package com.minky.studylog.service.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * ZIP 조립은 단위 테스트 둘이 덮지 못하는 자리에서 깨진다 — 이름이 겹치면
 * {@code ZipOutputStream} 이 그 자리에서 던지고, 태그는 {@code open-in-view=false} 라
 * 트랜잭션 밖에서 읽히는 순간 예외다. 둘 다 화면에서만, 그것도 특정 데이터에서만 드러난다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarkdownExportServiceTest {

    @Autowired MarkdownExportService exportService;
    @Autowired StudyLogService studyLogService;

    @Test
    @DisplayName("기록마다 파일 하나 · 같은 날 같은 제목도 이름이 갈림")
    void writesOneFilePerLog() throws Exception {
        seed("JPA", LocalDate.of(2026, 8, 3), LocalTime.of(9, 0), "spring", "jpa");
        seed("JPA", LocalDate.of(2026, 8, 3), LocalTime.of(23, 5), "spring", "jpa");
        seed("큐", LocalDate.of(2026, 8, 4), LocalTime.of(14, 0), "cs", "자료구조");

        assertThat(export()).containsOnlyKeys(
                "2026-08-03-JPA.md", "2026-08-03-JPA-2305.md", "2026-08-04-큐.md");
    }

    /**
     * 태그는 지연 로딩이라 트랜잭션 경계가 어긋나면 여기서만 드러난다. 프론트매터에 실려 나온
     * 것 자체가 쓰기 전체가 한 트랜잭션 안에 있었다는 증거다.
     */
    @Test
    @DisplayName("본문은 프론트매터 + 노트 — 지연 로딩 태그까지 실려 나감")
    void writesFrontMatterAndNote() throws Exception {
        seed("트랜잭션 격리 수준", LocalDate.of(2026, 8, 3), LocalTime.of(23, 0), "spring",
                "JPA, 트랜잭션");

        assertThat(export().get("2026-08-03-트랜잭션 격리 수준.md")).isEqualTo("""
                ---
                title: "트랜잭션 격리 수준"
                date: 2026-08-03
                start: "23:00"
                end: "00:00"
                durationMinutes: 60
                category: "spring"
                tags: ["jpa", "트랜잭션"]
                summary: "요약"
                ---
                # 노트""");
    }

    @Test
    @DisplayName("기록이 없어도 빈 ZIP 이 나감 — 백업 버튼이 오류로 끝나지 않게")
    void writesEmptyZipWithoutLogs() throws Exception {
        assertThat(export()).isEmpty();
    }

    private Map<String, String> export() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.writeZip(out);

        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private void seed(String title, LocalDate date, LocalTime start, String category, String tags) {
        StudyLogForm form = new StudyLogForm();
        form.setTitle(title);
        form.setStudyDate(date);
        form.setStartTime(start);
        form.setEndTime(start.plusHours(1));
        form.setCategoryName(category);
        form.setTagsCsv(tags);
        form.setSummary("요약");
        form.setNote("# 노트");
        studyLogService.create(form);
    }
}
