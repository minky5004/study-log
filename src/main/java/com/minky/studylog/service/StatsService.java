package com.minky.studylog.service;

import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.repository.projection.CategoryTotal;
import com.minky.studylog.repository.projection.DailyTotal;
import com.minky.studylog.repository.projection.StartTimeSlice;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 집계. 일별·분야별 합계만 DB {@code group by} 로 접고, 주·월 버킷팅과 시간대 분포는
 * 자바에서 계산한다 — {@code date_trunc}·{@code extract} 는 H2 와 PostgreSQL 을 매번 대조해야
 * 하고, 방언 밖으로 나오면 순수 단위 테스트로 경계값을 직접 먹일 수 있다.
 *
 * <p>버킷팅 메서드가 {@code static} 인 이유가 그것이다. DB 를 띄우지 않고 검증한다.
 */
@Service
public class StatsService {

    private static final int HOURS_PER_DAY = 24;
    private static final DateTimeFormatter WEEK_BUCKET = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StudyLogRepository studyLogRepository;

    public StatsService(StudyLogRepository studyLogRepository) {
        this.studyLogRepository = studyLogRepository;
    }

    /** 히트맵은 기록 없는 날을 빈 칸으로 두므로 0 을 채우지 않는다 — 잔디의 질문이 "한 날/안 한 날". */
    @Transactional(readOnly = true)
    public List<DailyTotal> heatmap(LocalDate from, LocalDate to) {
        return studyLogRepository.findDailyTotals(from, to);
    }

    /**
     * 조회 범위를 첫 버킷 시작으로 넓혀서 읽는다. 라벨만 접고 범위를 그대로 두면 첫 막대가
     * 부분 주가 된다 — 시작일이 화요일이면 그 주 월요일 기록이 합계에서 조용히 빠진다.
     */
    @Transactional(readOnly = true)
    public List<BucketTotal> weekly(LocalDate from, LocalDate to) {
        LocalDate start = weekStart(from);
        return toWeekly(studyLogRepository.findDailyTotals(start, to), start, to);
    }

    @Transactional(readOnly = true)
    public List<BucketTotal> monthly(LocalDate from, LocalDate to) {
        LocalDate start = monthStart(from);
        return toMonthly(studyLogRepository.findDailyTotals(start, to), start, to);
    }

    @Transactional(readOnly = true)
    public List<CategoryTotal> byCategory() {
        return studyLogRepository.findCategoryTotals();
    }

    @Transactional(readOnly = true)
    public long[] byHourOfDay() {
        return toHourly(studyLogRepository.findStartTimeSlices());
    }

    /** 주간 버킷은 ISO 기준 그 주의 월요일. 국가별 주 시작 요일 설정에 기대지 않는다. */
    static List<BucketTotal> toWeekly(List<DailyTotal> daily, LocalDate from, LocalDate to) {
        return bucketize(daily, from, to, StatsService::weekStart,
                date -> date.format(WEEK_BUCKET), date -> date.plusWeeks(1));
    }

    static List<BucketTotal> toMonthly(List<DailyTotal> daily, LocalDate from, LocalDate to) {
        return bucketize(daily, from, to, StatsService::monthStart,
                date -> date.format(MONTH_BUCKET), date -> date.plusMonths(1));
    }

    /**
     * 시작 시각이 속한 시(hour)에 세션 전체 분을 귀속시킨다. 자정 넘김 세션을 쪼개 배분하지 않는
     * 것은 이 차트의 질문이 "언제 앉는가" 이기 때문 — 쪼개면 23시에 시작해 1시에 끝낸 사람의
     * 습관이 0시대에 절반 옮겨 간다.
     */
    static long[] toHourly(List<StartTimeSlice> slices) {
        long[] minutesByHour = new long[HOURS_PER_DAY];
        for (StartTimeSlice slice : slices) {
            minutesByHour[slice.startTime().getHour()] += slice.totalMinutes();
        }
        return minutesByHour;
    }

    /**
     * 빈 칸을 관측된 범위가 아니라 <b>요청 범위</b>로 채운다. 관측 기준이면 최근 12주를 물었는데
     * 기록이 있는 주부터 그려져 "요즘 안 했다" 가 눈금에서 사라진다.
     */
    private static List<BucketTotal> bucketize(List<DailyTotal> daily, LocalDate from, LocalDate to,
            Function<LocalDate, LocalDate> bucketStart,
            Function<LocalDate, String> label,
            Function<LocalDate, LocalDate> next) {

        Map<LocalDate, Long> sums = new HashMap<>();
        for (DailyTotal total : daily) {
            sums.merge(bucketStart.apply(total.date()), total.totalMinutes(), Long::sum);
        }

        List<BucketTotal> buckets = new ArrayList<>();
        LocalDate cursor = bucketStart.apply(from);
        LocalDate last = bucketStart.apply(to);
        while (!cursor.isAfter(last)) {
            buckets.add(new BucketTotal(label.apply(cursor), sums.getOrDefault(cursor, 0L)));
            cursor = next.apply(cursor);
        }
        return buckets;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }
}
