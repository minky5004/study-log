package com.minky.studylog.service;

/**
 * 주·월 추이의 한 칸. {@code bucket} 은 주간이면 그 주 월요일의 {@code yyyy-MM-dd},
 * 월간이면 {@code yyyy-MM} 이다.
 *
 * <p>눈금 문자열을 집계 쪽에서 만들어 내보내는 것은 화면이 두 단위를 같은 방식으로 그리게 하기
 * 위해서다 — 날짜 타입으로 내보내면 월간에서 "1일" 이라는 없는 뜻이 붙는다.
 */
public record BucketTotal(String bucket, long totalMinutes) {
}
