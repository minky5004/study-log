package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.repository.projection.DailyTotal;
import com.minky.studylog.repository.projection.StartTimeSlice;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 버킷팅을 static 순수 함수로 두는 이유가 이 파일이다 — DB 없이 경계값을 직접 먹인다.
 * 주·월 묶기를 DB 방언에 맡겼다면 여기 있는 단언들을 H2 와 PostgreSQL 양쪽에서 다시 확인해야 한다.
 */
class StatsServiceTest {

    @Test
    @DisplayName("주간 버킷은 ISO 월요일 시작으로 묶임")
    void weeklyBucketsStartOnMonday() {
        List<DailyTotal> daily = List.of(
                daily("2026-08-03", 60),   // 월
                daily("2026-08-09", 30),   // 일 — 같은 주
                daily("2026-08-10", 90));  // 다음 주 월

        assertThat(StatsService.toWeekly(daily, date("2026-08-03"), date("2026-08-10")))
                .containsExactly(new BucketTotal("2026-08-03", 90),
                                 new BucketTotal("2026-08-10", 90));
    }

    @Test
    @DisplayName("월간 버킷은 연-월로 묶임")
    void monthlyBuckets() {
        List<DailyTotal> daily = List.of(daily("2026-07-31", 60), daily("2026-08-01", 40));

        assertThat(StatsService.toMonthly(daily, date("2026-07-31"), date("2026-08-01")))
                .containsExactly(new BucketTotal("2026-07", 60), new BucketTotal("2026-08", 40));
    }

    @Test
    @DisplayName("기록 없는 주는 0 으로 채워 추이가 끊기지 않게")
    void fillsEmptyWeeks() {
        List<DailyTotal> daily = List.of(daily("2026-08-03", 60), daily("2026-08-17", 90));

        assertThat(StatsService.toWeekly(daily, date("2026-08-03"), date("2026-08-17")))
                .containsExactly(new BucketTotal("2026-08-03", 60),
                                 new BucketTotal("2026-08-10", 0),
                                 new BucketTotal("2026-08-17", 90));
    }

    /**
     * 빈 칸을 관측된 범위가 아니라 <b>요청 범위</b>로 채운다. 관측 기준으로 채우면 최근 12주를
     * 물었는데 기록이 있는 주부터 그려져 "요즘 안 했다" 가 눈금에서 사라진다.
     */
    @Test
    @DisplayName("빈 칸 채우기 기준은 요청 범위 — 기록이 하나도 없어도 눈금은 그대로")
    void fillsRequestedRangeEvenWithoutRecords() {
        assertThat(StatsService.toMonthly(List.of(), date("2026-06-15"), date("2026-08-08")))
                .containsExactly(new BucketTotal("2026-06", 0),
                                 new BucketTotal("2026-07", 0),
                                 new BucketTotal("2026-08", 0));
    }

    @Test
    @DisplayName("시간대 분포는 시작 시각 시(hour)에 전체 분을 귀속 — 자정 넘김도 쪼개지 않음")
    void hourBucketUsesStartHour() {
        int[] hours = StatsService.toHourly(List.of(slice("23:30", 120)));

        assertThat(hours[23]).isEqualTo(120);
        assertThat(hours[0]).isZero();
    }

    @Test
    @DisplayName("같은 시간대 세션은 합산")
    void sumsSlicesInSameHour() {
        int[] hours = StatsService.toHourly(List.of(slice("14:00", 50), slice("14:59", 30)));

        assertThat(hours[14]).isEqualTo(80);
    }

    @Test
    @DisplayName("빈 입력에도 24칸 배열 — 화면이 칸 수를 다시 세지 않게")
    void emptyInputStillReturns24Slots() {
        assertThat(StatsService.toHourly(List.of())).hasSize(24).containsOnly(0);
    }

    private static DailyTotal daily(String date, long minutes) {
        return new DailyTotal(date(date), minutes);
    }

    private static StartTimeSlice slice(String startTime, int minutes) {
        return new StartTimeSlice(LocalTime.parse(startTime), minutes);
    }

    private static LocalDate date(String date) {
        return LocalDate.parse(date);
    }
}
