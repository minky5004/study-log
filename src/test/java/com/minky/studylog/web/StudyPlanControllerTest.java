package com.minky.studylog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.minky.studylog.config.SecurityConfig;
import com.minky.studylog.domain.PlanPriority;
import com.minky.studylog.domain.StudyPlan;
import com.minky.studylog.service.StudyPlanNotFoundException;
import com.minky.studylog.service.StudyPlanService;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudyPlanController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class StudyPlanControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean StudyPlanService studyPlanService;

    @Test
    @DisplayName("목록은 비로그인도 열림")
    void listIsPublic() throws Exception {
        given(studyPlanService.findPending()).willReturn(List.of(
                new StudyPlan("공부 메모장", "왜 · 어디까지", PlanPriority.HIGH)));
        given(studyPlanService.findDone()).willReturn(List.of());

        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/index"))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("공부 메모장"),
                        // 체크 자리는 비로그인에게도 남는다 — 빠지면 줄마다 들여쓰기가 어긋난다
                        Matchers.containsString("plan-check-locked"),
                        Matchers.not(Matchers.containsString("/toggle")))));
    }

    /**
     * 완료 절은 스텁이 빈 목록이면 통째로 렌더링되지 않는다 — 날짜 포맷과 취소선 마크업이
     * 어느 테스트에서도 돌지 않은 채 남고, 표현식 오류가 상단 네비에 걸린 화면의 500 으로만
     * 드러난다. 여기서 한 번 그려 둔다.
     */
    @Test
    @DisplayName("완료 항목은 취소선 절에 연월일과 함께")
    void rendersDoneSection() throws Exception {
        StudyPlan done = new StudyPlan("옵시디언 활용법", "왜 · 어디까지", PlanPriority.LOW);
        done.toggle();
        given(studyPlanService.findPending()).willReturn(List.of());
        given(studyPlanService.findDone()).willReturn(List.of(done));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("plan-done-section"),
                        Matchers.containsString("plan-check-on"),
                        Matchers.containsString(String.valueOf(LocalDate.now().getYear())))));
    }

    /** 우선순위 띠 클래스가 템플릿의 문자열 조립이 아니라 enum 에서 온다 — 로캘을 타지 않게. */
    @Test
    @DisplayName("우선순위 띠 클래스는 enum 이 소유")
    void rendersPriorityStyleClass() throws Exception {
        given(studyPlanService.findPending()).willReturn(List.of(
                new StudyPlan("공부 메모장", null, PlanPriority.HIGH)));
        given(studyPlanService.findDone()).willReturn(List.of());

        mockMvc.perform(get("/plans"))
                .andExpect(content().string(Matchers.containsString("prio-high")));
    }

    /**
     * 추가 폼이 목록 안에 있어 GET 하나가 두 역할을 진다 — 비로그인에게 입력창이 보이면
     * 눌러야 로그인 화면이 나오는 막다른 길이 된다.
     */
    @Test
    @DisplayName("비로그인에게는 추가 폼이 안 보임")
    void hidesAddFormFromAnonymous() throws Exception {
        given(studyPlanService.findPending()).willReturn(List.of());
        given(studyPlanService.findDone()).willReturn(List.of());

        mockMvc.perform(get("/plans"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("plan-add"))));
    }

    @Test
    @WithMockUser
    @DisplayName("추가 후 목록으로 되돌림")
    void createsThenRedirects() throws Exception {
        mockMvc.perform(post("/plans").with(csrf())
                        .param("title", "옵시디언 활용법")
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans"));
    }

    /** 검증 실패는 같은 화면으로 되돌아온다 — 목록 모델이 빠지면 그 화면이 빈 껍데기가 된다. */
    @Test
    @WithMockUser
    @DisplayName("빈 제목은 목록 화면으로 복귀 · 저장 없음")
    void rejectsBlankTitle() throws Exception {
        given(studyPlanService.findPending()).willReturn(List.of());
        given(studyPlanService.findDone()).willReturn(List.of());

        mockMvc.perform(post("/plans").with(csrf()).param("title", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/index"))
                .andExpect(model().attributeExists("pending", "done", "priorities"));

        then(studyPlanService).should(never()).create(any());
    }

    /**
     * {@code select} 가 값을 좁히므로 화면으로는 오지 않는 입력이다. 그래도 화면에 오류 자리를
     * 두는 것은, 없으면 저장도 오류도 없이 200 이 나가 사용자가 추가된 것으로 읽기 때문.
     */
    @Test
    @WithMockUser
    @DisplayName("목록 밖 우선순위는 화면에 오류로 — 조용히 200 으로 되돌아가지 않게")
    void showsErrorForUnknownPriority() throws Exception {
        given(studyPlanService.findPending()).willReturn(List.of());
        given(studyPlanService.findDone()).willReturn(List.of());

        mockMvc.perform(post("/plans").with(csrf())
                        .param("title", "공부 메모장")
                        .param("priority", "URGENT"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/index"))
                .andExpect(content().string(Matchers.containsString("field-error")));

        then(studyPlanService).should(never()).create(any());
    }

    @Test
    @WithMockUser
    @DisplayName("체크·삭제는 목록으로 되돌림")
    void togglesAndDeletes() throws Exception {
        mockMvc.perform(post("/plans/7/toggle").with(csrf()))
                .andExpect(redirectedUrl("/plans"));
        mockMvc.perform(post("/plans/7/delete").with(csrf()))
                .andExpect(redirectedUrl("/plans"));

        then(studyPlanService).should().toggle(7L);
        then(studyPlanService).should().delete(7L);
    }

    /** 두 창에서 같은 항목을 지우면 뒤엣것이 이 경로로 들어온다 — 500 이면 고장과 구별되지 않는다. */
    @Test
    @WithMockUser
    @DisplayName("이미 사라진 항목의 체크는 404")
    void missingPlanIsNotFound() throws Exception {
        org.mockito.BDDMockito.willThrow(new StudyPlanNotFoundException(404L))
                .given(studyPlanService).toggle(404L);

        mockMvc.perform(post("/plans/404/toggle").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/4xx"));
    }
}
