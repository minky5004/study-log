package com.minky.studylog.repository.projection;

import java.time.LocalDate;

/**
 * 하루치 공부 시간 합계. 기록이 없는 날은 아예 행이 없다 — 빈 날 채우기는 화면이 무엇을 묻는지에
 * 따라 달라지므로(히트맵은 빈 칸, 추이는 0) 집계 단계에서 정하지 않는다.
 *
 * <p>인터페이스 프로젝션 대신 레코드를 쓰는 것은 이 값이 그대로 JSON 으로 나가기 때문이다 —
 * 프로젝션 프록시를 직렬화하면 게터가 아닌 것까지 따라 나갈 여지가 있다.
 */
public record DailyTotal(LocalDate date, long totalMinutes) {
}
