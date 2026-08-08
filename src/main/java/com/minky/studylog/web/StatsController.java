package com.minky.studylog.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 통계 화면. 숫자는 모델이 아니라 {@code /api/stats/*} 에서 스크립트가 받아 간다 —
 * 화면이 제 범위를 정하면 같은 "최근 1년" 이 화면과 JSON 에서 하루씩 어긋난다.
 */
@Controller
public class StatsController {

    @GetMapping("/stats")
    public String index() {
        return "stats/index";
    }
}
