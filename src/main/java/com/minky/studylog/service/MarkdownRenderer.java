package com.minky.studylog.service;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 노트 마크다운을 화면에 넣을 HTML 로 바꾼다.
 * <p>
 * 렌더링과 새니타이즈를 두 단계로 나눈 것은 commonmark 가 마크다운 문법 밖의 raw HTML 을
 * 그대로 통과시키기 때문 — 렌더러만으로는 저장된 {@code <script>} 가 그대로 나간다.
 */
@Component
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;
    private final Safelist safelist;

    public MarkdownRenderer() {
        // create() 의 반환형이 Extension 이라 원소 타입을 좁히면 파서 빌더가 받지 못한다
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer = HtmlRenderer.builder().extensions(extensions).build();
        // relaxed 는 javascript: 스킴과 on* 속성을 이미 거른다. 표·코드블록이 쓰는
        // class 속성만 더 연다 — 문법 강조를 나중에 붙일 자리를 지금 지워두지 않기 위해
        this.safelist = Safelist.relaxed()
                .addTags("hr")
                .addAttributes("code", "class")
                .addAttributes("pre", "class")
                .addAttributes("table", "class");
    }

    public String toSafeHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return Jsoup.clean(htmlRenderer.render(parser.parse(markdown)), safelist);
    }
}
