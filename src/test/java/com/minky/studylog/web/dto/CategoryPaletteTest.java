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
        assertThat(consecutiveColorsFrom(1L)).doesNotHaveDuplicates().hasSize(CategoryPalette.SIZE);
        // 재기동으로 IDENTITY 블록 잔여 번호가 버려진 뒤의 실측 채번. 1 에서 시작하는 구간만
        // 덮으면 작은 식별자를 특별 취급하는 구현도 이 테스트를 통과한다
        assertThat(consecutiveColorsFrom(33L)).doesNotHaveDuplicates().hasSize(CategoryPalette.SIZE);
    }

    private static List<Integer> consecutiveColorsFrom(long firstId) {
        return LongStream.range(firstId, firstId + CategoryPalette.SIZE)
                .mapToObj(CategoryPalette::indexOf).toList();
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

    /**
     * 규칙과 토큰을 함께 센다. 규칙만 세면 {@code .cat-6} 을 토큰 없이 늘린 날 그 규칙이
     * 정의되지 않은 {@code var(--cat-6)} 을 가리켜, 색 없는 점이 테스트를 통과한 채로 나간다.
     */
    @Test
    @DisplayName("팔레트 크기와 스타일시트의 색 규칙 · 토큰 개수 일치 — 한쪽만 늘리면 색 없는 점이 조용히 나간다")
    void matchesStylesheetRules() throws Exception {
        String css;
        try (InputStream in = CategoryPalette.class.getResourceAsStream("/static/css/app.css")) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(IntStream.range(0, CategoryPalette.SIZE).boxed().toList())
                .allSatisfy(index -> assertThat(css).contains(".cat-" + index, "--cat-" + index + ":"));
        assertThat(css).doesNotContain(".cat-" + CategoryPalette.SIZE, "--cat-" + CategoryPalette.SIZE);
    }
}
