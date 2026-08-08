package com.minky.studylog.web;

import com.minky.studylog.repository.projection.CategoryTotal;
import com.minky.studylog.repository.projection.DailyTotal;
import com.minky.studylog.service.BucketTotal;
import com.minky.studylog.service.StatsService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 차트가 읽는 JSON. 화면(Task 13)과 나누는 것은 집계 결과를 브라우저 없이 검증할 수 있게 하기
 * 위해서다 — 눈금이 이상할 때 차트 코드와 집계 중 어느 쪽인지 여기서 먼저 가른다.
 *
 * <p>기본 범위를 서버가 정한다. 화면이 정하면 같은 "최근 1년" 이 화면마다 하루씩 어긋난다.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsApiController {

    /** 잔디 한 판. 오늘을 포함해 세므로 하루를 뺀다. */
    private static final int HEATMAP_DAYS = 365;

    /** 추이 눈금 수. 주간이면 12주, 월간이면 12개월. */
    private static final int TREND_BUCKETS = 12;

    private final StatsService statsService;

    public StatsApiController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/heatmap")
    public List<DailyTotal> heatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.minusDays(HEATMAP_DAYS - 1L);
        if (start.isAfter(end)) {
            throw badRequest("from 이 to 보다 뒤입니다");
        }
        return statsService.heatmap(start, end);
    }

    /**
     * 시작일을 버킷 경계로 맞추지 않고 그대로 넘긴다 — 집계가 어차피 버킷 시작으로 접으므로,
     * 여기서 한 번 더 접으면 같은 규칙이 두 곳에 생긴다.
     */
    @GetMapping("/trend")
    public List<BucketTotal> trend(@RequestParam(defaultValue = "week") String unit) {
        LocalDate today = today();
        return switch (unit) {
            case "week" -> statsService.weekly(today.minusWeeks(TREND_BUCKETS - 1L), today);
            case "month" -> statsService.monthly(today.minusMonths(TREND_BUCKETS - 1L), today);
            default -> throw badRequest("unit 은 week 또는 month");
        };
    }

    @GetMapping("/categories")
    public List<CategoryTotal> categories() {
        return statsService.byCategory();
    }

    @GetMapping("/hours")
    public long[] hours() {
        return statsService.byHourOfDay();
    }

    /**
     * 주소창에서 온 잘못된 값이라 400 이다. 기본 처리에 맡기면 500 이 나가 서버 고장과
     * 구별되지 않는다.
     *
     * <p>{@code IllegalArgumentException} 을 통째로 잡는 핸들러를 두지 않는다 — 서비스나
     * 하이버네이트에서 올라오는 같은 타입까지 400 으로 가려 서버 결함이 주소창 조작과
     * 구별되지 않고 스택트레이스도 남지 않는다.
     */
    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /** 배포 호스트가 UTC 라 기본값을 박지 않으면 자정 직후의 "오늘" 이 하루 어긋난다. */
    private static LocalDate today() {
        return LocalDate.now(StudyLogForm.ZONE);
    }
}
