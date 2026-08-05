package com.minky.studylog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudySessionTimeTest {

    @Test
    @DisplayName("같은 날 안에서 끝나는 세션은 시각 차이 그대로")
    void sameDay() {
        assertThat(StudySessionTime.durationMinutes(LocalTime.of(9, 0), LocalTime.of(11, 30)))
                .isEqualTo(150);
    }

    @Test
    @DisplayName("종료가 시작보다 앞서면 다음 날로 간주")
    void acrossMidnight() {
        assertThat(StudySessionTime.durationMinutes(LocalTime.of(23, 0), LocalTime.of(1, 0)))
                .isEqualTo(120);
    }

    @Test
    @DisplayName("자정 직전까지 이어지는 세션")
    void untilMidnight() {
        assertThat(StudySessionTime.durationMinutes(LocalTime.of(22, 0), LocalTime.of(0, 0)))
                .isEqualTo(120);
    }

    @Test
    @DisplayName("1분짜리 세션도 허용")
    void oneMinute() {
        assertThat(StudySessionTime.durationMinutes(LocalTime.of(9, 0), LocalTime.of(9, 1)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("1분 미만 세션은 0분 — 초 단위 차이를 자정 넘김으로 오인하지 않음")
    void subMinuteSessionIsZero() {
        assertThat(StudySessionTime.durationMinutes(LocalTime.of(9, 0, 0), LocalTime.of(9, 0, 30)))
                .isZero();
    }

    @Test
    @DisplayName("초 단위로만 역전된 세션도 익일 간주")
    void subMinuteBackwardCrossesMidnight() {
        assertThat(StudySessionTime.durationMinutes(LocalTime.of(9, 0, 30), LocalTime.of(9, 0, 0)))
                .isEqualTo(24 * 60);
    }

    @Test
    @DisplayName("시작과 종료가 같으면 0분인지 24시간인지 정할 수 없으므로 거부")
    void sameTimeRejected() {
        assertThatThrownBy(() ->
                StudySessionTime.durationMinutes(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
