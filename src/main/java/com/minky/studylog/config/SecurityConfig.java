package com.minky.studylog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Task 11 까지는 인증을 걸지 않는다. 설정이 없으면 자동 구성된 폼 로그인이 모든 경로를
     * 막아 화면 확인 자체가 불가능해지기 때문이다. Task 11 에서 읽기/쓰기 분리로 교체한다.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
