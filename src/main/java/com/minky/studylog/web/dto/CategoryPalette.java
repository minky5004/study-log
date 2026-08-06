package com.minky.studylog.web.dto;

/**
 * 분야 색 배정. 분야는 사용자가 만드는 값이라 사람이 미리 색을 정할 수 없어 등록 순서로 준다.
 * <p>
 * 이름 해시로 흩뿌리지 않는 것은 6칸에 무작위로 던지면 분야 넷만 되어도 충돌 확률이 70%를
 * 넘기 때문 — 실제로 {@code "cs"} 와 {@code "os"} 가 같은 칸에 떨어진다. 순서로 배정하면
 * 식별자가 연속인 동안은 여섯 개까지 서로 다른 색이다. 재기동으로 IDENTITY 블록의 남은 번호가
 * 버려지면 건너뛴 만큼 색도 건너뛰므로 보장이 아니라 경향 — 그래도 겹침이 무작위보다 늦게 온다.
 * <p>
 * 색은 서버에서 배정한다. 목록(Thymeleaf)과 차트(Chart.js)가 같은 값을 써야 하므로
 * 클라이언트에서 다시 계산할 자리를 두지 않는다.
 * <p>
 * 분야가 {@link #SIZE} 를 넘으면 색이 겹치는 것은 감수한다 — 이름을 늘 함께 표기해 색만으로
 * 분야를 구별하는 화면이 없고, 색을 늘리면 서로 구별이 어려워지는 쪽이 더 큰 손해다.
 */
public final class CategoryPalette {

    /**
     * 실제 색은 {@code static/css/app.css} 의 {@code .cat-0}~{@code .cat-5} 가 갖는다.
     * 이 값만 늘리면 남는 인덱스가 아무 규칙에도 걸리지 않아 색 없는 점으로 조용히 나가므로,
     * 두 쪽이 어긋나는지는 {@code CategoryPaletteTest} 가 지킨다.
     */
    public static final int SIZE = 6;

    private CategoryPalette() {
    }

    public static int indexOf(Long categoryId) {
        if (categoryId == null) {
            return 0;
        }
        // 식별자 채번이 1부터라 1 을 빼야 첫 분야가 첫 색. floorMod 는 채번 시작값이
        // 달라 음수가 들어와도 인덱스를 범위 안에 둔다
        return Math.floorMod(categoryId - 1, SIZE);
    }
}
