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

    /** BCrypt 해시 접두. `$2a` · `$2b` · `$2y` 를 한 번에 가린다. */
    private static final String BCRYPT_PREFIX = "$2";

    /**
     * 풀리지 않은 플레이스홀더 접두. 바인더가 값을 찾지 못하면 예외를 던지는 것이 아니라
     * `${APP_ADMIN_USERNAME}` 문자열을 그대로 넘긴다 — 빈 값 검사만 두면 이것이 통과해
     * 그 문자열이 아이디인 계정으로 앱이 멀쩡히 뜬다.
     */
    private static final String UNRESOLVED_PREFIX = "${";

    public AdminAccountProperties {
        if (isMissing(username) || isMissing(passwordHash)) {
            throw new IllegalStateException(
                    "APP_ADMIN_USERNAME · APP_ADMIN_PASSWORD_HASH 환경변수 미설정");
        }
        // 평문을 넣는 것은 배포에서 흔한 손버릇인데, 그대로 두면 앱은 정상 기동하고 로그인만
        // 영원히 실패한다 — 인코더가 남기는 것도 WARN 한 줄뿐이라 화면에서 원인이 보이지 않는다
        if (!passwordHash.startsWith(BCRYPT_PREFIX)) {
            throw new IllegalStateException(
                    "APP_ADMIN_PASSWORD_HASH 는 BCrypt 해시여야 합니다 — 평문이 아니라 `$2`로 시작하는 값");
        }
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || value.startsWith(UNRESOLVED_PREFIX);
    }
}
