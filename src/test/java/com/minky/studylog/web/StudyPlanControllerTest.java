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
                .andExpect(content().string(Matchers.containsString("공부 메모장")));
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
