package com.minky.studylog.config;

import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableConfigurationProperties(AdminAccountProperties.class)
public class SecurityConfig {

    /**
     * 상태를 바꾸는 요청. 서버 렌더링 앱이라 변경은 전부 폼 POST 이므로 메서드 하나로 갈린다.
     * 조회 메서드를 열거하는 방향으로 적는 것은 앞으로 들어올 PUT·DELETE 도 함께 걸리게 하기 위해서.
     */
    private static final RequestMatcher STATE_CHANGING = SecurityConfig::changesState;

    private static boolean changesState(HttpServletRequest request) {
        String method = request.getMethod();
        return !HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method);
    }

    /**
     * 읽기는 공개, 상태 변경은 인증. 반대로 {@code anyRequest().authenticated()} 를 바닥에 깔면
     * 없는 주소까지 로그인 화면으로 튀어 오타 URL 이 404 대신 로그인 폼이 된다 — 공개 사이트에
     * 영구히 남는 흠이다.
     *
     * <p>규칙을 잊었을 때의 실패 방향도 이쪽이 낫다. 새 쓰기 경로는 비-GET 이라 규칙 없이도
     * 막히고, 새 쓰기 <b>화면</b>을 빠뜨리면 비로그인에게 폼이 보이되 제출은 거부된다.
     *
     * <p>CSRF 토큰은 세션이 아니라 쿠키에 담는다. {@code CsrfFilter} 는 인증 필터들보다 앞에
     * 서므로(등록 순서가 {@code CsrfFilter} → {@code LogoutFilter} →
     * {@code UsernamePasswordAuthenticationFilter} → {@code RememberMeAuthenticationFilter}),
     * 토큰이 세션에 있으면 세션이 사라진 뒤의 저장은 remember-me 가 인증을 되살리기 전에 403 으로
     * 끊긴다. 그 403 에는 방금 쓴 노트가 담겨 있지 않고 되돌아갈 자리도 없다.
     * {@code withHttpOnlyFalse()} 는 쓰지 않는다 — 자바스크립트가 토큰을 읽어야 하는 것은
     * SPA 쪽 사정이고, 여기서는 Thymeleaf {@code th:action} 이 서버에서 심는다.
     *
     * <p>remember-me 는 영속 토큰 방식이다. 해시 기반은 테이블이 필요 없는 대신 취소 수단이
     * key 나 비밀번호 교체뿐인데, 이쪽은 쓸 때마다 토큰을 갈아 끼워 탈취된 쿠키의 재사용이
     * 드러나고 취소가 행 하나를 지우는 일이 된다.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, PersistentTokenRepository tokens,
                                    UserDetailsService users) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 쓰기 화면은 GET 이라 메서드 규칙에 걸리지 않는다 — 경로로 따로 세운다.
                        // 백업 내려받기도 GET 이지만 한 요청이 DB 전량을 흘려보내 화면 단위 조회와 다르고,
                        // 가져오기 화면은 POST 만 막으면 비로그인에게 업로드 폼이 보이되 제출만 거부된다
                        .requestMatchers("/logs/new", "/logs/*/edit", "/logs/*/delete",
                                "/export", "/import")
                        .authenticated()
                        .requestMatchers(STATE_CHANGING).authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                // 유효기간은 기본값(2주) 그대로 둔다 — 쓸 때마다 갱신되므로 매일 여는 도구에서는
                // 사실상 만료되지 않고, 여기 숫자를 새로 정하면 근거 없는 값이 하나 는다
                .rememberMe(remember -> remember
                        .tokenRepository(tokens)
                        .userDetailsService(users))
                // 목록으로 돌려보낸다 — 로그아웃 직후 남는 화면이 로그인 폼이면 방금 한 일과 어긋난다
                .logout(logout -> logout.logoutSuccessUrl("/logs"));
        return http.build();
    }

    /**
     * 스키마를 만드는 자리는 Flyway 하나여야 하므로 {@code setCreateTableOnStartup(true)} 는 쓰지
     * 않는다. 이 테이블의 정본은 {@code V3__persistent_logins.sql} 이고, 컬럼 이름·타입은 이
     * 클래스가 문자열로 박아 둔 기본 SQL 넷과 짝이다.
     */
    @Bean
    PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl tokens = new JdbcTokenRepositoryImpl();
        tokens.setDataSource(dataSource);
        return tokens;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 사용자가 하나뿐이라 저장소를 두지 않는다. 회원가입·다중 사용자는 만들지 않기로 한 목록에 있다.
     */
    @Bean
    UserDetailsService userDetailsService(AdminAccountProperties admin) {
        return new InMemoryUserDetailsManager(User.withUsername(admin.username())
                .password(admin.passwordHash())
                .roles("ADMIN")
                .build());
    }
}
