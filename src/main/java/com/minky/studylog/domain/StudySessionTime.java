package com.minky.studylog.domain;

import java.time.Duration;
import java.time.LocalTime;

public final class StudySessionTime {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private StudySessionTime() {
    }

    /**
     * 종료 시각이 시작 시각보다 앞서거나 같으면 다음 날 같은 시각으로 간주한다.
     * 같은 시각은 0분과 24시간을 구별할 수 없으므로 입력 단계에서 거부한다.
     */
    public static int durationMinutes(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("시작·종료 시각은 필수");
        }
        if (start.equals(end)) {
            throw new IllegalArgumentException("시작 시각과 종료 시각이 같을 수 없음");
        }
        int minutes = (int) Duration.between(start, end).toMinutes();
        // 분 절삭값의 부호로 익일을 판정하면 1분 미만 세션(09:00:00~09:00:30)이 0 으로 절삭돼
        // 자정 넘김으로 오인된다. 절삭 전 시각을 직접 비교한다
        return end.isAfter(start) ? minutes : minutes + MINUTES_PER_DAY;
    }
}
