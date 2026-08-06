package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 노트는 {@code th:utext} 로 나가므로 여기가 유일한 방어선이다.
 * 이 클래스의 테스트를 지우거나 약하게 만들면 저장된 문자열이 그대로 스크립트가 된다.
 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    @DisplayName("기본 마크다운 렌더링")
    void rendersBasicMarkdown() {
        assertThat(renderer.toSafeHtml("# 제목\n\n본문 **강조**"))
                .contains("<h1>제목</h1>")
                .contains("<strong>강조</strong>");
    }

    @Test
    @DisplayName("GFM 표 렌더링 — 확장 없이는 파이프가 문단으로 남는다")
    void rendersTables() {
        assertThat(renderer.toSafeHtml("| a | b |\n|---|---|\n| 1 | 2 |")).contains("<table>");
    }

    @Test
    @DisplayName("script 태그 제거 — 태그도 안의 호출도 남기지 않는다")
    void stripsScriptTag() {
        assertThat(renderer.toSafeHtml("<script>alert(1)</script>"))
                .doesNotContain("script")
                .doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("이벤트 핸들러 속성 제거")
    void stripsEventHandler() {
        assertThat(renderer.toSafeHtml("<img src=\"x\" onerror=\"alert(1)\">"))
                .doesNotContain("onerror");
    }

    @Test
    @DisplayName("javascript: 스킴 링크 제거 — 마크다운 문법을 거쳐 들어오는 경로")
    void stripsJavascriptScheme() {
        assertThat(renderer.toSafeHtml("[클릭](javascript:alert(1))")).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("코드블록 안의 꺾쇠는 텍스트로 살아남음 — 새니타이즈가 예제 코드를 지우지 않게")
    void keepsCodeBlockContent() {
        assertThat(renderer.toSafeHtml("```\n<script>x</script>\n```")).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("기록 사이 상대 링크 유지 — 절대 주소만 남기면 평문으로 조용히 내려앉는다")
    void keepsSiteRelativeLink() {
        assertThat(renderer.toSafeHtml("[앞 세션](/logs/12)")).contains("href=\"/logs/12\"");
    }

    @Test
    @DisplayName("null · 빈 노트는 빈 문자열")
    void handlesEmpty() {
        assertThat(renderer.toSafeHtml(null)).isEmpty();
        assertThat(renderer.toSafeHtml("")).isEmpty();
    }

    @Test
    @DisplayName("위키링크는 대괄호 그대로 — 옵시디언 전용 문법이라 웹에서 링크로 만들지 않는다")
    void leavesWikiLinkAsIs() {
        assertThat(renderer.toSafeHtml("[[다른 노트]]")).contains("[[다른 노트]]");
    }
}
