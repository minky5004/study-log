package com.minky.studylog.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    /**
     * 도중에 끊긴 내보내기가 중앙 디렉터리까지 갖추면 압축 도구가 정상으로 연다 — 백업을
     * 풀어 본 사람은 몇 건이 빠졌는지 알 길이 없다. 온전한 것처럼 보이는 일부보다 깨진 파일이 낫다.
     */
    @Test
    @DisplayName("끊긴 내보내기는 중앙 디렉터리 없이 끝남")
    void leavesInterruptedZipUnfinished() throws Exception {
        seed("JPA", LocalDate.of(2026, 8, 3), LocalTime.of(9, 0), "spring", "jpa");
        seed("큐", LocalDate.of(2026, 8, 4), LocalTime.of(14, 0), "cs", "자료구조");

        CutOffStream cut = new CutOffStream(150);
        assertThatThrownBy(() -> exportService.writeZip(cut)).isInstanceOf(IOException.class);

        // 정상 경로에는 있고 끊긴 경로에는 없어야 단언이 성립한다
        assertThat(latin1(fullExport())).contains(END_OF_CENTRAL_DIRECTORY);
        assertThat(latin1(cut.written())).doesNotContain(END_OF_CENTRAL_DIRECTORY);
    }

    /**
     * ZIP 맨 끝의 End of Central Directory 표지. 이것이 없으면 압축 도구가 파일 목록을
     * 읽지 못한다. 앞 두 바이트만 보면 엔트리마다 붙는 지역 헤더에도 걸린다.
     */
    private static final String END_OF_CENTRAL_DIRECTORY = "PK";

    private byte[] fullExport() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.writeZip(out);
        return out.toByteArray();
    }

    /** 바이트를 그대로 문자로 옮겨 표지를 찾는다 — UTF-8 로 읽으면 압축된 바이트가 뭉개진다. */
    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private Map<String, String> export() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(fullExport()), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    /** 내려받는 쪽이 도중에 끊긴 상황. 정해진 바이트를 넘기면 더 받지 않고 던진다. */
    private static final class CutOffStream extends OutputStream {

        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final int limit;

        private CutOffStream(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            if (received.size() >= limit) {
                throw new IOException("연결 끊김");
            }
            received.write(b);
        }

        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                write(bytes[off + i]);
            }
        }

        private byte[] written() {
            return received.toByteArray();
        }
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
