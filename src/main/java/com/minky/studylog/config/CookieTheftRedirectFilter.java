package com.minky.studylog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 같은 series 의 옛 remember-me 토큰이 한 번 더 왔을 때 500 대신 로그인 화면을 내주는 자리.
 *
 * <p>{@code AbstractRememberMeServices.autoLogin} 은 그 상황에서 쿠키를 지운 뒤
 * {@link CookieTheftException} 을 <b>다시 던진다</b>. 나머지 넷(만료·형식 오류 등)은 삼키고
 * {@code null} 을 돌려주는데 이것만 다르다. 그런데
 * {@code RememberMeAuthenticationFilter} 의 예외 표는 {@code authenticationManager.authenticate}
 * 구간만 덮고 {@code autoLogin} 호출은 덮지 않아, 이 예외는 잡히지 않고 필터 밖으로 나간다.
 * {@code ExceptionTranslationFilter} 는 뒤에 서 있어 손이 닿지 않는다.
 *
 * <p>그대로 두면 이 사이클이 없애려던 결말이 403 에서 500 으로 자리만 옮긴다. 탭 둘이 열린 채
 * 앱이 재기동되면 한쪽이 토큰을 갈아 끼운 뒤 다른 쪽이 옛 값을 들고 오고, 뒤로 가기 복원이나
 * 재시도도 같은 모양이다 — 도난이 아니어도 닿는 자리라는 뜻이다.
 *
 * <p>예외를 삼켜 익명으로 계속 가게 하지 않는 것은 {@code chain.doFilter} 안쪽에서 던져진 것이라
 * 중간부터 이어 갈 수 없기 때문이다. 쿠키는 이미 지워진 뒤이므로 로그인 화면이 옳은 착지점이다.
 */
class CookieTheftRedirectFilter extends OncePerRequestFilter {

    private final String loginPath;

    CookieTheftRedirectFilter(String loginPath) {
        this.loginPath = loginPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (CookieTheftException ex) {
            if (response.isCommitted()) {
                throw ex;
            }
            response.sendRedirect(request.getContextPath() + loginPath);
        }
    }
}
