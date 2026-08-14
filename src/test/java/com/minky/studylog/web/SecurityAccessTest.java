package com.minky.studylog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
// Security 7 에서 servlet.result → servlet.response 로 옮겨졌다
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        mockMvc.perform(get("/plans")).andExpect(status().isOk());
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
     * 둘 다 GET 이라 메서드 규칙에 걸리지 않는다. 내보내기는 경로 규칙이 빠지면 한 번의 익명
     * 요청이 DB 전량을 흘려보내고, 가져오기는 비로그인에게 업로드 폼이 보이되 제출만 거부된다.
     */
    @Test
    @DisplayName("비로그인 백업 내려받기 · 가져오기 화면 차단")
    void backupEndpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/export")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/import")).andExpect(redirectedUrl("/login"));
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

    /**
     * 계획은 쓰기 화면이 목록 안에 있어 경로 규칙이 하나도 없다 — 막는 것이 전적으로
     * 메서드 규칙이라, 그 규칙이 풀리면 여기서만 드러난다.
     */
    @Test
    @DisplayName("비로그인 계획 추가 · 체크 · 삭제 차단")
    void planWritesRequireLogin() throws Exception {
        mockMvc.perform(post("/plans").with(csrf()).param("title", "x"))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/plans/1/toggle").with(csrf()))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/plans/1/delete").with(csrf()))
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

    /**
     * 컨테이너 healthcheck 가 로그인 없이 물어보는 자리라 열려 있어야 하고, 열린 만큼
     * 나가는 것은 UP/DOWN 뿐이어야 한다. 두 조건은 어긋나는 방향이 반대라 한 자리에 묶는다.
     */
    @Test
    @DisplayName("health 는 비로그인 공개 · 세부는 응답에 싣지 않음")
    void healthIsPublicWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    /**
     * 컨테이너 healthcheck 가 실제로 두드리는 주소는 복합 health 가 아니라 이 그룹이다.
     * 그룹 정의가 지워지면 healthcheck 는 404 를 받아 앱이 멀쩡해도 unhealthy 로 굳는다.
     */
    @Test
    @DisplayName("healthcheck 가 쓰는 container 그룹이 살아 있음 · 세부는 여기도 감춤")
    void containerHealthGroupIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/container"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    /**
     * 노출 목록을 health 하나로 못박은 것이 지켜지는지 본다. 스타터가 늘 때마다 끝점이 따라
     * 느는 것이 액추에이터의 기본 성질이라, 설정이 풀리면 설정 파일이 아니라 여기서 걸린다.
     * 목록 끝점 {@code /actuator} 를 함께 세우는 것은 그쪽이 노출되지 않은 끝점의 이름까지
     * 링크로 흘려, 여기 적힌 "health 밖은 없다" 를 사실이 아니게 만들기 때문.
     */
    @Test
    @DisplayName("health 밖의 액추에이터 끝점 · 목록 끝점은 공개도 인증도 아닌 404")
    @WithMockUser(roles = "ADMIN")
    void otherActuatorEndpointsAreNotMapped() throws Exception {
        mockMvc.perform(get("/actuator")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
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
