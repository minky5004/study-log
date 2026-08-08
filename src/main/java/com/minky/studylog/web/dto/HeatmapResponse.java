package com.minky.studylog.web.dto;

import com.minky.studylog.repository.projection.DailyTotal;
import java.time.LocalDate;
import java.util.List;

/**
 * 잔디 한 판. 기록이 있는 날만 {@code days} 에 실리므로 격자를 그리려면 범위가 함께 있어야 한다.
 * <p>
 * 브라우저가 "오늘" 부터 다시 세게 두지 않는 이유는 방문자 시간대가 서버와 다르면 잔디가
 * 통째로 한 칸 밀리기 때문이다 — 서버에서는 재현되지 않는 어긋남이다.
 */
public record HeatmapResponse(LocalDate from, LocalDate to, List<DailyTotal> days) {
}
