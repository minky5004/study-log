package com.minky.studylog.web;

import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.service.StudyPlanService;
import com.minky.studylog.web.dto.StudyLogDay;
import com.minky.studylog.web.dto.StudyLogListItem;
import com.minky.studylog.web.dto.StudyLogSearchCond;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** 날짜 상자로 묶여 나가므로 다섯 건이 대개 상자 두세 개다 — 칸 하나 높이에 맞는 수 */
    private static final int RECENT_LOGS = 5;

    /** 여기서 체크하지 않으므로 목록이 아니라 미리보기. 전부 보는 자리는 계획 화면 */
    private static final int PREVIEW_PLANS = 5;

    private final StudyLogService studyLogService;
    private final StudyPlanService studyPlanService;

    public HomeController(StudyLogService studyLogService, StudyPlanService studyPlanService) {
        this.studyLogService = studyLogService;
        this.studyPlanService = studyPlanService;
    }

    /**
     * 첫 화면. {@code /logs} 로 리다이렉트만 하던 자리인데, 그러면 처음 들어온 사람이 검색 폼과
     * 페이지네이션부터 보게 돼 이 앱이 통계·계획도 한다는 신호가 어디에도 없다.
     *
     * <p><b>집계는 하나도 새로 만들지 않는다.</b> 잔디는 통계 화면과 같은
     * {@code /api/stats/heatmap} 을 스크립트가 부르고, 나머지 둘은 있는 조회의 앞부분이다 —
     * 홈 전용 집계를 두면 같은 숫자를 두 곳에서 세게 된다.
     */
    @GetMapping("/")
    public String home(Model model) {
        List<StudyLogListItem> recent = studyLogService.findAll(new StudyLogSearchCond(),
                PageRequest.of(0, RECENT_LOGS, StudyLogService.LATEST_FIRST)).getContent();

        model.addAttribute("days", StudyLogDay.groupByDate(recent));
        model.addAttribute("plans",
                studyPlanService.findPending().stream().limit(PREVIEW_PLANS).toList());
        return "home";
    }

    /** 로그인 화면은 뷰만 있으면 된다 — 자격증명 처리는 시큐리티 필터가 가져간다. */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
