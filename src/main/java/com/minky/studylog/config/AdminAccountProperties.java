package com.minky.studylog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 유일한 관리자 계정. 값은 환경변수로만 들어온다 — 기본 계정을 두면 그것이 곧 리포에 박힌
 * 자격증명이 되므로, 미설정이면 부팅이 실패하는 편이 낫다.
 *
 * <p>비밀번호는 BCrypt 해시로 받는다. 평문을 받아 앱이 인코딩하면 그 평문이 환경변수와 프로세스
 * 목록에 그대로 남는다.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminAccountProperties(String username, String passwordHash) {

    public AdminAccountProperties {
        if (username == null || username.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalStateException(
                    "APP_ADMIN_USERNAME · APP_ADMIN_PASSWORD_HASH 환경변수 미설정");
        }
    }
}
