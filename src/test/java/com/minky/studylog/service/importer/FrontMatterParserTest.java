package com.minky.studylog.service.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파서의 입력 계약은 {@link com.minky.studylog.service.export.FrontMatterWriter} 의 출력이다.
 * 한쪽만 고치면 왕복이 조용히 깨지므로 형식 단언을 여기서도 8키로 고정한다.
 * <p>
 * 남의 vault 에서 온 노트도 받는다 — 옵시디언은 태그를 블록 리스트로 쓰고, 편집기에 따라
 * BOM·CRLF·따옴표 없는 값이 섞인다.
 */
class FrontMatterParserTest {

    private final FrontMatterParser parser = new FrontMatterParser();

    @Test
    @DisplayName("내보낸 형식 그대로 파싱")
    void parsesExportedFormat() {
        ParsedNote note = parser.parse("""
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
                # 노트
                본문""");

        assertThat(note.title()).isEqualTo("트랜잭션 격리 수준");
        assertThat(note.date()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(note.start()).isEqualTo(LocalTime.of(23, 0));
        assertThat(note.end()).isEqualTo(LocalTime.of(1, 0));
        assertThat(note.category()).isEqualTo("Spring");
        assertThat(note.tags()).containsExactly("jpa", "트랜잭션");
        assertThat(note.summary()).isEqualTo("격리 수준 정리");
        assertThat(note.body()).isEqualTo("# 노트\n본문");
    }

    /**
     * 남의 vault 는 {@code aliases} {@code cssclass} 같은 키를 얹어 둔다. 모르는 키에 걸려
     * 실패하면 이관이 파일 하나 단위로 막힌다. {@code durationMinutes} 도 여기 속한다 —
     * 저장 시점 계산 파생값이라 파일의 값을 믿지 않고 시각에서 다시 잰다.
     */
    @Test
    @DisplayName("모르는 키는 무시 — durationMinutes 도 읽지 않음")
    void ignoresUnknownKeys() {
        ParsedNote note = parser.parse(frontMatter("durationMinutes: 9999\naliases: [x]"));

        assertThat(note.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(note.end()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("옵시디언 블록 리스트 태그 표기도 파싱")
    void parsesBlockListTags() {
        ParsedNote note = parser.parse("""
                ---
                title: 블록 리스트
                date: 2026-08-03
                start: 09:00
                end: 10:00
                tags:
                  - jpa
                  - "트랜잭션"
                category: CS
                ---
                본문""");

        assertThat(note.tags()).containsExactly("jpa", "트랜잭션");
        assertThat(note.category()).isEqualTo("CS");
    }

    @Test
    @DisplayName("따옴표 없는 값도 파싱")
    void parsesUnquotedValues() {
        ParsedNote note = parser.parse("""
                ---
                title: 따옴표 없는 제목
                date: 2026-08-03
                start: 09:00
                end: 10:30
                category: 알고리즘
                tags: [ps, 그래프]
                ---
                """);

        assertThat(note.title()).isEqualTo("따옴표 없는 제목");
        assertThat(note.end()).isEqualTo(LocalTime.of(10, 30));
        assertThat(note.tags()).containsExactly("ps", "그래프");
    }

    @Test
    @DisplayName("이스케이프한 따옴표 · 역슬래시 복원 — 내보내기가 넣은 것을 그대로 되돌림")
    void unescapesQuotedValues() {
        assertThat(parser.parse("---\ntitle: \"\\\"인용\\\" \\\\ 백슬래시\"\n"
                + "date: 2026-08-03\nstart: 09:00\nend: 10:00\n---\n").title())
                .isEqualTo("\"인용\" \\ 백슬래시");
    }

    @Test
    @DisplayName("태그 없으면 빈 집합")
    void emptyTags() {
        assertThat(parser.parse(frontMatter("tags: []")).tags()).isEmpty();
    }

    @Test
    @DisplayName("요약 키가 없으면 null — 빈 문자열로 채우지 않음")
    void missingSummaryStaysNull() {
        assertThat(parser.parse(frontMatter("")).summary()).isNull();
    }

    @Test
    @DisplayName("프론트매터 없으면 실패 사유와 함께 예외")
    void missingFrontMatter() {
        assertThatThrownBy(() -> parser.parse("# 그냥 노트"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("프론트매터 없음");
    }

    @Test
    @DisplayName("닫는 --- 이 없으면 실패")
    void unterminatedFrontMatter() {
        assertThatThrownBy(() -> parser.parse("---\ntitle: 제목\ndate: 2026-08-03\n"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("닫히지 않음");
    }

    @Test
    @DisplayName("title · date 가 없으면 실패")
    void missingRequiredKeys() {
        assertThatThrownBy(() -> parser.parse("---\ndate: 2026-08-03\n---\n"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> parser.parse("---\ntitle: 제목\n---\n"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("date");
    }

    @Test
    @DisplayName("날짜 · 시각 형식이 깨지면 실패")
    void malformedDateOrTime() {
        assertThatThrownBy(() -> parser.parse(
                "---\ntitle: 제목\ndate: 2026-13-45\nstart: 09:00\nend: 10:00\n---\n"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> parser.parse(
                "---\ntitle: 제목\ndate: 2026-08-03\nstart: 25:99\nend: 10:00\n---\n"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("start");
    }

    /**
     * 시각 없는 노트를 duration 0 으로 받으려면 두 컬럼을 nullable 로 열어야 하고, 그 null 은
     * 통계·목록·내보내기까지 번진다. 이관 통로는 별도 사이클의 몫이라 여기서는 사유를 남기고 뺀다.
     */
    @Test
    @DisplayName("start · end 가 없으면 사유와 함께 실패")
    void missingTimesRejected() {
        assertThatThrownBy(() -> parser.parse("---\ntitle: 제목\ndate: 2026-08-03\n---\n"))
                .isInstanceOf(ImportFormatException.class)
                .hasMessageContaining("시각");
    }

    @Test
    @DisplayName("category 가 없으면 '미분류'")
    void missingCategoryFallsBack() {
        assertThat(parser.parse(frontMatter("")).category()).isEqualTo("미분류");
    }

    @Test
    @DisplayName("본문 안의 --- 는 프론트매터 종료로 오인하지 않음")
    void bodyHorizontalRule() {
        ParsedNote note = parser.parse(frontMatter("") + "앞\n\n---\n\n뒤");

        assertThat(note.title()).isEqualTo("제목");
        assertThat(note.body()).isEqualTo("앞\n\n---\n\n뒤");
    }

    @Test
    @DisplayName("BOM · CRLF 가 붙어도 파싱")
    void handlesBomAndCrlf() {
        ParsedNote note = parser.parse("﻿---\r\ntitle: 제목\r\ndate: 2026-08-03\r\n"
                + "start: 09:00\r\nend: 10:00\r\n---\r\n첫 줄\r\n둘째 줄");

        assertThat(note.title()).isEqualTo("제목");
        assertThat(note.body()).isEqualTo("첫 줄\n둘째 줄");
    }

    /** 프론트매터만 있고 본문이 없는 노트. 빈 문자열이 아니라 null 이어야 노트 없는 기록이 된다. */
    @Test
    @DisplayName("본문이 없으면 null")
    void emptyBodyStaysNull() {
        assertThat(parser.parse(frontMatter("")).body()).isNull();
    }

    /** 필수 키 넷과 시각을 갖춘 최소 노트에 한 줄을 더 얹는다. */
    private static String frontMatter(String extraLine) {
        return "---\ntitle: 제목\ndate: 2026-08-03\nstart: 09:00\nend: 10:00\n"
                + (extraLine.isEmpty() ? "" : extraLine + "\n") + "---\n";
    }
}
