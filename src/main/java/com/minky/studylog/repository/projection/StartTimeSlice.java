package com.minky.studylog.repository.projection;

import java.time.LocalTime;

/**
 * 시간대 분포의 재료. 시작 시각과 전체 분만 있으면 되므로 기록 전체를 읽지 않는다.
 *
 * <p>합계를 DB 에서 접지 않는 것은 시(hour) 추출이 방언마다 다르기 때문 — 행 수는 기록 수만큼이지만
 * 자바에서 세는 편이 H2 와 PostgreSQL 을 같게 만든다.
 */
public record StartTimeSlice(LocalTime startTime, int durationMinutes) {
}
