package com.minky.studylog.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAccountPropertiesTest {

    private static final String HASH =
            "$2a$10$IsNeckCAf7AVZIO/0TdMsuDpdWWpvj3moMhtdx6p5qK0bnqHKwTlS";

    @Test
    @DisplayName("아이디 · 해시 미설정은 부팅 실패 — 기본 계정을 두는 것보다 낫다")
    void rejectsMissingValues() {
        assertThatThrownBy(() -> new AdminAccountProperties(null, HASH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminAccountProperties(" ", HASH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminAccountProperties("admin", null))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 바인더는 환경변수를 찾지 못해도 예외를 던지지 않고 `${...}` 문자열을 그대로 넘긴다.
     * 빈 값 검사만 두면 미설정이 곧 "그 문자열이 아이디인 계정" 으로 기동해 버린다 —
     * 실측으로 확인한 경로다.
     */
    @Test
    @DisplayName("풀리지 않은 플레이스홀더는 미설정과 같게 취급")
    void rejectsUnresolvedPlaceholder() {
        assertThatThrownBy(() -> new AdminAccountProperties("${APP_ADMIN_USERNAME}", HASH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AdminAccountProperties("admin", "${APP_ADMIN_PASSWORD_HASH}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("평문 비밀번호는 부팅 실패 — 통과시키면 기동은 되고 로그인만 영원히 실패")
    void rejectsPlaintextPassword() {
        assertThatThrownBy(() -> new AdminAccountProperties("admin", "hunter2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BCrypt");
    }

    @Test
    @DisplayName("BCrypt 해시는 통과")
    void acceptsBcryptHash() {
        assertThatCode(() -> new AdminAccountProperties("admin", HASH)).doesNotThrowAnyException();
    }
}
