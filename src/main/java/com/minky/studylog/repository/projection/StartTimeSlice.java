package com.minky.studylog.repository.projection;

import java.time.LocalTime;

/**
 * 시간대 분포의 재료. 같은 시각에 시작한 세션은 DB 에서 이미 합쳐져 온다 — 시(hour) 로 접는 일만
 * 자바가 맡아 방언 차이를 없앤다.
 */
public record StartTimeSlice(LocalTime startTime, long totalMinutes) {
}
