package com.minky.studylog.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryPaletteTest {

    @Test
    @DisplayName("연속된 식별자 여섯은 서로 다른 색 — 이름 해시로 흩뿌리면 넷째에서 이미 겹친다")
    void assignsDistinctColorsForConsecutiveIds() {
        List<Integer> colors = LongStream.rangeClosed(1, CategoryPalette.SIZE)
                .mapToObj(CategoryPalette::indexOf).toList();

        assertThat(colors).doesNotHaveDuplicates().hasSize(CategoryPalette.SIZE);
    }

    @Test
    @DisplayName("같은 분야는 항상 같은 색 — 재시작해도 목록의 색이 바뀌지 않게")
    void assignsStableIndex() {
        assertThat(CategoryPalette.indexOf(3L)).isEqualTo(CategoryPalette.indexOf(3L));
    }

    @Test
    @DisplayName("일곱째부터 앞의 색을 다시 쓴다 — 겹침은 감수하고 범위를 벗어나지 않는 것만 지킨다")
    void wrapsAroundPastPalette() {
        assertThat(CategoryPalette.indexOf(7L)).isEqualTo(CategoryPalette.indexOf(1L));
        assertThat(CategoryPalette.indexOf(1_000_000L)).isBetween(0, CategoryPalette.SIZE - 1);
    }

    @Test
    @DisplayName("채번이 0 이나 음수로 시작해도 인덱스는 범위 안 — 나머지 대신 floorMod")
    void staysWithinPaletteForAnyId() {
        assertThat(CategoryPalette.indexOf(0L)).isBetween(0, CategoryPalette.SIZE - 1);
        assertThat(CategoryPalette.indexOf(-1L)).isBetween(0, CategoryPalette.SIZE - 1);
    }

    @Test
    @DisplayName("식별자가 없으면 첫 색 — 색 계산이 목록 렌더링을 막지 않게")
    void fallsBackForMissingId() {
        assertThat(CategoryPalette.indexOf(null)).isZero();
    }

    @Test
    @DisplayName("팔레트 크기와 스타일시트의 색 규칙 개수 일치 — 한쪽만 늘리면 색 없는 점이 조용히 나간다")
    void matchesStylesheetRules() throws Exception {
        String css;
        try (InputStream in = CategoryPalette.class.getResourceAsStream("/static/css/app.css")) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(IntStream.range(0, CategoryPalette.SIZE).mapToObj(i -> ".cat-" + i).toList())
                .allSatisfy(selector -> assertThat(css).contains(selector));
        assertThat(css).doesNotContain(".cat-" + CategoryPalette.SIZE);
    }
}
