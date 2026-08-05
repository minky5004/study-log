package com.minky.studylog.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyLogDayTest {

    @Test
    @DisplayName("같은 날짜를 한 상자로 묶고 들어온 순서 유지 — 조회 정렬을 다시 손대지 않음")
    void groupsByDateKeepingOrder() {
        List<StudyLogDay> days = StudyLogDay.groupByDate(List.of(
                item("오늘 저녁", LocalDate.of(2026, 8, 6), LocalTime.of(21, 0), 60),
                item("오늘 낮", LocalDate.of(2026, 8, 6), LocalTime.of(14, 0), 80),
                item("어제", LocalDate.of(2026, 8, 5), LocalTime.of(9, 0), 30)));

        assertThat(days).extracting(StudyLogDay::date)
                .containsExactly(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 5));
        assertThat(days.get(0).logs()).extracting(StudyLogListItem::title)
                .containsExactly("오늘 저녁", "오늘 낮");
    }

    @Test
    @DisplayName("상자 머리 합계는 그날 세션의 합 · 시간으로 표기")
    void sumsDayTotal() {
        List<StudyLogDay> days = StudyLogDay.groupByDate(List.of(
                item("낮", LocalDate.of(2026, 8, 6), LocalTime.of(14, 0), 80),
                item("밤", LocalDate.of(2026, 8, 6), LocalTime.of(21, 0), 90)));

        assertThat(days.get(0).totalMinutes()).isEqualTo(170);
        assertThat(days.get(0).totalText()).isEqualTo("2시간 50분");
    }

    @Test
    @DisplayName("세션이 하나뿐인 날만 세션 행 소요 시간 생략 대상")
    void marksSingleSessionDay() {
        List<StudyLogDay> days = StudyLogDay.groupByDate(List.of(
                item("혼자", LocalDate.of(2026, 8, 6), LocalTime.of(14, 0), 80),
                item("첫째", LocalDate.of(2026, 8, 5), LocalTime.of(9, 0), 30),
                item("둘째", LocalDate.of(2026, 8, 5), LocalTime.of(20, 0), 30)));

        assertThat(days.get(0).singleSession()).isTrue();
        assertThat(days.get(1).singleSession()).isFalse();
    }

    @Test
    @DisplayName("날짜가 하루도 없으면 상자도 없음")
    void groupsEmptyList() {
        assertThat(StudyLogDay.groupByDate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("상자 머리 날짜는 세션과 같은 한국어 표기")
    void formatsDateText() {
        List<StudyLogDay> days = StudyLogDay.groupByDate(List.of(
                item("제목", LocalDate.of(2026, 8, 6), LocalTime.of(14, 0), 80)));

        assertThat(days.get(0).dateText()).isEqualTo("8월 6일 (목)");
    }

    private StudyLogListItem item(String title, LocalDate date, LocalTime start, int minutes) {
        return new StudyLogListItem(1L, title, date, start, start.plusMinutes(minutes), minutes,
                "CS", 1L, List.of("큐"), "요약");
    }
}
