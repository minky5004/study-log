package com.minky.studylog.repository.projection;

/** 분야별 공부 시간 합계. 색 배정은 식별자로 하므로 이름과 함께 id 를 들고 나온다. */
public record CategoryTotal(Long categoryId, String categoryName, long totalMinutes) {
}
