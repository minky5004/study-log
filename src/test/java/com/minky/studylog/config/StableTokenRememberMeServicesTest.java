package com.minky.studylog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

class StableTokenRememberMeServicesTest {

    /** 실제 DB 를 죽일 수 없어 저장소를 가짜로 둔다. 나머지 경로는 RememberMeTest 가 실제 엔진 위에서 본다. */
    @Test
    @DisplayName("사용 시각 갱신이 DB 오류로 실패하면 500 이 아니라 익명으로 이어진다")
    void updateFailureFallsBackToAnonymous() {
        PersistentTokenRepository tokens = mock(PersistentTokenRepository.class);
        when(tokens.getTokenForSeries("series"))
                .thenReturn(new PersistentRememberMeToken("tester", "series", "token", new Date()));
        doThrow(new DataAccessResourceFailureException("DB 연결 끊김"))
                .when(tokens).updateToken(any(), any(), any());
        StableTokenRememberMeServices services = new StableTokenRememberMeServices(
                "key", mock(UserDetailsService.class), tokens);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("remember-me", Base64.getEncoder().withoutPadding()
                .encodeToString("series:token".getBytes(StandardCharsets.UTF_8))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(services.autoLogin(request, response)).isNull();
        assertThat(response.getCookie("remember-me").getMaxAge()).isZero();
    }
}
