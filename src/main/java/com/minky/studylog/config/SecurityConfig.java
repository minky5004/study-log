package com.minky.studylog.config;

import jakarta.servlet.http.HttpServletRequest;
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
     * <p>CSRF 는 기본값 그대로 켜 둔다 — Thymeleaf {@code th:action} 이 토큰을 심으므로
     * 화면에서 따로 할 일이 없다.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 쓰기 화면은 GET 이라 메서드 규칙에 걸리지 않는다 — 경로로 따로 세운다.
                        // 백업 내려받기도 GET 이지만 한 요청이 DB 전량을 흘려보내 화면 단위 조회와 다르다
                        .requestMatchers("/logs/new", "/logs/*/edit", "/logs/*/delete", "/export")
                        .authenticated()
                        .requestMatchers(STATE_CHANGING).authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form.loginPage("/login").permitAll())
                // 목록으로 돌려보낸다 — 로그아웃 직후 남는 화면이 로그인 폼이면 방금 한 일과 어긋난다
                .logout(logout -> logout.logoutSuccessUrl("/logs"));
        return http.build();
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
