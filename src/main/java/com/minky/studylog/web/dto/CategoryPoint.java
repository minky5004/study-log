package com.minky.studylog.web.dto;

import com.minky.studylog.repository.projection.CategoryTotal;

/**
 * 도넛 한 조각. 집계 결과에 색 배정을 얹어 내보낸다.
 * <p>
 * 식별자를 그대로 실어 보내지 않는 것은 색 배정 규칙이 한 곳에만 있어야 하기 때문이다 —
 * 스크립트가 {@code id} 로 다시 칸을 고르면 같은 규칙이 자바와 자바스크립트에 한 벌씩 생겨,
 * 한쪽만 고친 날 목록의 분야 색과 도넛의 분야 색이 어긋난다.
 */
public record CategoryPoint(String categoryName, long totalMinutes, int colorIndex) {

    public static CategoryPoint from(CategoryTotal total) {
        return new CategoryPoint(total.categoryName(), total.totalMinutes(),
                CategoryPalette.indexOf(total.categoryId()));
    }
}
