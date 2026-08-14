package com.minky.studylog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.minky.studylog.config.SecurityConfig;
import com.minky.studylog.domain.PlanPriority;
import com.minky.studylog.domain.StudyPlan;
import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.service.StudyPlanService;
import com.minky.studylog.web.dto.StudyLogListItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class HomeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean StudyLogService studyLogService;
    @MockitoBean StudyPlanService studyPlanService;

    /** 비어 있는 응답이 바닥값이다. 셋 중 하나를 채우는 테스트가 나머지를 건드리지 않게. */
    @BeforeEach
    void emptyByDefault() {
        given(studyLogService.findAll(any(), any())).willReturn(new PageImpl<>(List.of()));
        given(studyPlanService.findPending()).willReturn(List.of());
    }

    @Test
    @DisplayName("홈은 비로그인도 열림 — 배포 URL 을 처음 누른 사람이 닿는 자리")
    void homeIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }

    /**
     * 잔디는 스크립트가 그리므로 자리가 통째로 사라져도 응답은 200 이다. 이름이 어긋나면
     * 화면에서만, 그것도 조용히 드러난다 — {@link StatsControllerTest} 와 같은 이유.
     *
     * <p>Chart.js 를 함께 보는 것은 그것이 판단이기 때문. 홈이 네 카드를 다 그리면 통계 화면과
     * 겹쳐 둘 다 존재 이유를 잃는다.
     */
    @Test
    @DisplayName("잔디 진입점이 실려 나감 · Chart.js CDN 은 부르지 않음")
    void carriesHeatmapHookWithoutChartJs() throws Exception {
        mockMvc.perform(get("/")).andExpect(content().string(Matchers.allOf(
                Matchers.containsString("data-chart=\"heatmap\""),
                Matchers.containsString("data-range=\"heatmap\""),
                Matchers.containsString("/js/stats.js"),
                Matchers.not(Matchers.containsString("chart.umd")))));
    }

    @Test
    @DisplayName("최근 기록은 날짜 상자로 · 계획은 미완료만")
    void showsRecentLogsAndPendingPlans() throws Exception {
        given(studyLogService.findAll(any(), any())).willReturn(new PageImpl<>(List.of(
                new StudyLogListItem(1L, "트랜잭션 격리 수준", LocalDate.of(2026, 8, 3),
                        LocalTime.of(14, 0), LocalTime.of(16, 0), 120, "CS", 1L,
                        List.of("jpa"), "요약"))));
        given(studyPlanService.findPending()).willReturn(List.of(
                new StudyPlan("공부 메모장", "왜 · 어디까지", PlanPriority.HIGH)));

        mockMvc.perform(get("/")).andExpect(content().string(Matchers.allOf(
                Matchers.containsString("트랜잭션 격리 수준"),
                Matchers.containsString("8월 3일 (월)"),
                Matchers.containsString("공부 메모장"),
                Matchers.containsString("prio-high"))));
    }

    /**
     * 첫 방문자가 보는 화면이면서 기록이 하나도 없는 상태다 — 여기서 500 이 나면 배포 URL 을
     * 처음 누른 사람이 보는 것이 오류 화면이 된다.
     */
    @Test
    @DisplayName("기록도 계획도 없는 상태에서 200 · 두 빈 문구")
    void rendersEmptyState() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("아직 기록이 없습니다"),
                        Matchers.containsString("적어 둔 계획이 없습니다"))));
    }

    /** 체크·삭제 자리를 둘로 늘리지 않는다 — 홈은 읽기만 하고 상태는 계획 화면이 바꾼다. */
    @Test
    @DisplayName("계획 위젯에 체크·삭제 폼 없음")
    void planWidgetIsReadOnly() throws Exception {
        given(studyPlanService.findPending()).willReturn(List.of(
                new StudyPlan("옵시디언 활용법", null, PlanPriority.NORMAL)));

        mockMvc.perform(get("/")).andExpect(content().string(Matchers.allOf(
                Matchers.containsString("옵시디언 활용법"),
                Matchers.not(Matchers.containsString("/toggle")),
                Matchers.not(Matchers.containsString("/delete")))));
    }
}
