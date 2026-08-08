package com.minky.studylog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
// Security 7 에서 servlet.result → servlet.response 로 옮겨졌다
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 권한 경계는 화면별 테스트로 흩어 놓으면 어느 경로가 열려 있는지 한눈에 볼 수 없다.
 * 열린 것과 막힌 것을 한 파일에 모아 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("조회는 비로그인도 허용 — 보여 주는 것이 이 서비스의 목적")
    void readIsPublic() throws Exception {
        mockMvc.perform(get("/logs")).andExpect(status().isOk());
        mockMvc.perform(get("/stats")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인 생성 요청은 로그인 화면으로")
    void createRequiresLogin() throws Exception {
        mockMvc.perform(post("/logs").with(csrf()).param("title", "x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /**
     * 내보내기는 GET 이라 메서드 규칙에 걸리지 않는다. 경로 규칙이 빠지면 한 번의 익명 요청이
     * DB 전량을 흘려보내는데, 그 사실은 화면 어디에도 드러나지 않는다.
     */
    @Test
    @DisplayName("비로그인 백업 내려받기 차단")
    void exportRequiresLogin() throws Exception {
        mockMvc.perform(get("/export")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("비로그인 폼 · 수정 · 삭제 차단")
    void writeEndpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/logs/new")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/logs/1/edit")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/logs/1").with(csrf())).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/logs/1/delete").with(csrf()))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("없는 주소는 비로그인도 404 — 오류 디스패치까지 인증을 걸면 오타가 로그인 화면으로 둔갑")
    void unknownPathStaysNotFound() throws Exception {
        mockMvc.perform(get("/nowhere")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("통계 JSON 은 비로그인도 허용 — 노출 데이터가 목록·상세와 같다")
    void statsApiIsPublic() throws Exception {
        mockMvc.perform(get("/api/stats/hours")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stats/categories")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("정적 자원은 비로그인도 허용 — 막으면 로그인 화면부터 스타일이 깨진다")
    void staticResourcesArePublic() throws Exception {
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/tag-suggest.js")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF 토큰 없는 쓰기는 로그인 상태여도 거부")
    @WithMockUser(roles = "ADMIN")
    void rejectsMissingCsrf() throws Exception {
        mockMvc.perform(post("/logs").param("title", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 상태에서는 폼 접근 허용")
    @WithMockUser(roles = "ADMIN")
    void loggedInCanOpenForm() throws Exception {
        mockMvc.perform(get("/logs/new")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 화면 자체는 열려 있음 — 막으면 로그인할 수단이 없다")
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    /**
     * 환경변수 주입 · BCrypt 대조 · 역할 부여가 한 줄에 걸린다. 셋 중 하나만 어긋나도
     * 배포 후 아무도 로그인하지 못하는데, 그때는 화면으로만 드러난다.
     */
    @Test
    @DisplayName("설정한 계정으로 실제 로그인 성공 · ADMIN 부여")
    void logsInWithConfiguredAccount() throws Exception {
        mockMvc.perform(formLogin().user("tester").password("test-password"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    @DisplayName("틀린 비밀번호는 로그인 실패")
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(formLogin().user("tester").password("wrong"))
                .andExpect(unauthenticated());
    }
}
