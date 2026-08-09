package com.minky.studylog.service.importer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 마크다운 한 편에서 읽어 낸 값. 저장 형태로 다듬는 것은 서비스의 몫이라 태그도 원문 그대로 담는다.
 * <p>
 * {@code durationMinutes} 가 없는 것은 의도다 — 저장 시점 계산 파생값이라 파일에 실려 온 값과
 * 시각이 어긋나면 그 어긋남이 통계로 들어간다.
 */
public record ParsedNote(String title, LocalDate date, LocalTime start, LocalTime end,
                         String category, Set<String> tags, String summary, String body) {
}
