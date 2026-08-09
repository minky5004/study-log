package com.minky.studylog.service.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 내보낸 파일은 사용자 vault 에 그대로 남아 다음 형식과 갈라진다. 키 이름·따옴표 유무는
 * 편의가 아니라 계약이므로 여기서 고정한다.
 */
class FrontMatterWriterTest {

    private final FrontMatterWriter writer = new FrontMatterWriter();

    @Test
    @DisplayName("프론트매터 8개 키 · 본문은 note 원문 그대로")
    void writesFrontMatter() {
        String out = writer.write(log("트랜잭션 격리 수준", "2026-08-03", "23:00", "01:00",
                "Spring", tags("jpa", "트랜잭션"), "격리 수준 정리", "# 노트\n본문"));

        assertThat(out).startsWith("""
                ---
                title: "트랜잭션 격리 수준"
                date: 2026-08-03
                start: "23:00"
                end: "01:00"
                durationMinutes: 120
                category: "Spring"
                tags: ["jpa", "트랜잭션"]
                summary: "격리 수준 정리"
                ---
                """);
        assertThat(out).endsWith("# 노트\n본문");
    }

    /**
     * {@code date} 와 {@code durationMinutes} 만 따옴표가 없다 — 옵시디언 Dataview 가
     * 날짜·숫자로 인식해야 정렬과 범위 질의가 성립한다.
     */
    @Test
    @DisplayName("날짜 · 분은 따옴표 없이 — 문자열로 인식되면 질의가 안 된다")
    void writesDateAndNumberUnquoted() {
        String out = writer.write(logWithNote(null));

        assertThat(out).contains("date: 2026-08-03\n").doesNotContain("date: \"");
        assertThat(out).contains("durationMinutes: 60\n").doesNotContain("durationMinutes: \"");
    }

    @Test
    @DisplayName("제목 안의 큰따옴표 · 역슬래시 이스케이프")
    void escapesQuotes() {
        assertThat(writer.write(logWithTitle("\"인용\" \\ 백슬래시")))
                .contains("title: \"\\\"인용\\\" \\\\ 백슬래시\"");
    }

    @Test
    @DisplayName("태그 없으면 빈 배열")
    void emptyTags() {
        assertThat(writer.write(logWithTags(noTags()))).contains("tags: []");
    }

    /**
     * 알파벳 정렬을 두었던 자리다. 왕복마다 순서가 흔들리던 때는 정렬이 재내보내기 diff 를
     * 막는 유일한 수단이었지만, 순서가 컬럼에 실린 뒤로는 저장 순서 자체가 결정적이라
     * 같은 목적을 사용자가 적은 순서를 지우지 않고 이룬다.
     */
    @Test
    @DisplayName("태그는 저장 순서 그대로 — 알파벳 정렬 아님")
    void writesTagsInStoredOrder() {
        assertThat(writer.write(logWithTags(tags("스트림", "jpa", "큐"))))
                .contains("tags: [\"스트림\", \"jpa\", \"큐\"]");
    }

    @Test
    @DisplayName("요약이 비면 키 자체를 빼고 씀 — 빈 문자열 키는 가져오기에서 요약 있는 기록이 된다")
    void omitsBlankSummary() {
        assertThat(writer.write(logWithSummary(null))).doesNotContain("summary:");
        assertThat(writer.write(logWithSummary("  "))).doesNotContain("summary:");
    }

    @Test
    @DisplayName("note 가 null 이면 프론트매터만")
    void nullNote() {
        assertThat(writer.write(logWithNote(null))).endsWith("---\n");
    }

    /**
     * 프론트매터 파싱은 "첫 {@code ---} 다음의 첫 {@code ---} 까지" 이므로 본문 구분선은
     * 안전하다. 이 테스트가 그 계약을 고정한다.
     */
    @Test
    @DisplayName("note 안의 --- 구분선이 프론트매터를 깨뜨리지 않음")
    void noteWithHorizontalRule() {
        String out = writer.write(logWithNote("본문\n\n---\n\n뒷부분"));

        assertThat(out.indexOf("---")).isZero();
        assertThat(out.split("(?m)^---$", -1)).hasSize(4); // 시작 · 끝 · 본문 구분선
    }

    /**
     * 폼은 브라우저가 준 CRLF 를 그대로 저장한다. 손대지 않으면 한 파일 안에 줄끝 두 종류가
     * 섞여, 마크다운 도구마다 다르게 읽힌다.
     */
    @Test
    @DisplayName("노트의 CRLF 는 LF 로 통일")
    void normalizesLineEndings() {
        assertThat(writer.write(logWithNote("첫 줄\r\n둘째 줄"))).endsWith("첫 줄\n둘째 줄");
    }

    private static StudyLog logWithTitle(String title) {
        return log(title, "2026-08-03", "09:00", "10:00", "Spring", noTags(), null, null);
    }

    private static StudyLog logWithTags(SequencedSet<String> tags) {
        return log("제목", "2026-08-03", "09:00", "10:00", "Spring", tags, null, null);
    }

    private static StudyLog logWithSummary(String summary) {
        return log("제목", "2026-08-03", "09:00", "10:00", "Spring", noTags(), summary, null);
    }

    private static StudyLog logWithNote(String note) {
        return log("제목", "2026-08-03", "09:00", "10:00", "Spring", noTags(), null, note);
    }

    private static StudyLog log(String title, String date, String start, String end,
                                String category, SequencedSet<String> tags, String summary, String note) {
        return new StudyLog(title, LocalDate.parse(date), LocalTime.parse(start),
                LocalTime.parse(end), new Category(category), tags, summary, note);
    }

    /**
     * {@code Set.of} 를 감싸면 헬퍼가 순서를 뒤섞는다 — 순서를 재는 테스트가 자기 입력부터
     * 흔들리면 무엇도 재지 못한다.
     */
    private static SequencedSet<String> tags(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static SequencedSet<String> noTags() {
        return new LinkedHashSet<>();
    }
}
