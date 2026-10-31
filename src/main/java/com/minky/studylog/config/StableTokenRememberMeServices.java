package com.minky.studylog.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * 토큰을 갈아 끼우지 않는 remember-me. 사용 시각과 쿠키 수명만 늘린다.
 *
 * <p>부모 클래스는 쓸 때마다 토큰을 새로 만들고, 옛 값이 다시 오면 도난으로 보고 그 사용자의
 * 토큰 행을 전부 지운다. 그런데 Render 무료는 유휴 15분에 잠들어 세션이 사라지므로 깨어난 뒤
 * 첫 요청마다 토큰이 갈린다. 이때 요청이 겹치거나 응답이 브라우저에 닿지 못하면 브라우저에
 * 옛 값이 남고, 도난이 아닌데도 로그인 유지가 풀린다.
 *
 * <p>토큰을 고정하면 옛 값이라는 상태 자체가 없어져 경로와 무관하게 해결된다. 대신 탈취된 쿠키의
 * 재사용을 자동으로 알아채지 못한다. 관리자 한 명의 HttpOnly 쿠키라 이쪽을 택했고, 취소 수단인
 * 행 삭제 · 로그아웃은 그대로 남는다.
 */
class StableTokenRememberMeServices extends PersistentTokenBasedRememberMeServices {

    private final PersistentTokenRepository tokens;

    StableTokenRememberMeServices(String key, UserDetailsService users,
                                  PersistentTokenRepository tokens) {
        super(key, users, tokens);
        this.tokens = tokens;
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response) {
        if (cookieTokens.length != 2) {
            throw new InvalidCookieException("Cookie token did not contain 2 tokens");
        }
        PersistentRememberMeToken token = tokens.getTokenForSeries(cookieTokens[0]);
        if (token == null || !matches(token.getTokenValue(), cookieTokens[1])) {
            throw new RememberMeAuthenticationException("Unknown remember-me token");
        }
        long expiresAt = token.getDate().getTime() + getTokenValiditySeconds() * 1000L;
        if (expiresAt < System.currentTimeMillis()) {
            throw new RememberMeAuthenticationException("Remember-me login has expired");
        }
        try {
            tokens.updateToken(token.getSeries(), token.getTokenValue(), new Date());
        } catch (DataAccessException ex) {
            // 이 예외만 autoLogin 이 삼켜 익명으로 이어 간다 — 그대로 두면 공개 화면까지 500
            throw new RememberMeAuthenticationException("Autologin failed due to data access problem", ex);
        }
        setCookie(cookieTokens, getTokenValiditySeconds(), request, response);
        return getUserDetailsService().loadUserByUsername(token.getUsername());
    }

    private static boolean matches(String expected, String presented) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
