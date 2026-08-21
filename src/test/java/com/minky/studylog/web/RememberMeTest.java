package com.minky.studylog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.minky.studylog.support.PostgresTestContainer;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 세션이 사라져도 로그인과 작성 중인 노트가 함께 사라지지 않는지 본다.
 *
 * <p>이 클래스만 <b>실제 PostgreSQL 위에서</b> 도는 웹 테스트다. {@code persistent_logins} 에는
 * 엔티티가 없어 {@code test} 프로파일의 {@code create-drop} 이 그 테이블을 만들지 않으므로,
 * H2 컨텍스트에서는 remember-me 로그인이 없는 테이블에 부딪힌다. 스키마를 만드는 자리가
 * Flyway 하나라는 규칙을 지키려면 여기가 실제 엔진 위여야 한다.
 *
 * <p>대가는 컨테이너 하나가 더 뜨는 것이다 — 컨텍스트 설정이 {@code @DataJpaTest} 쪽과 달라
 * 캐시를 함께 타지 못한다.
 *
 * <p>MockMvc 의 {@code csrf()} 후처리기를 쓰지 않고 화면이 내려보낸 토큰을 그대로 다시 올린다.
 * 이 클래스가 재는 것이 <b>토큰이 어디에 담겨 오가는가</b>라, 후처리기가 저장소를 건너뛰면
 * 재려던 것을 그대로 놓친다. 같은 이유로 쿠키도 브라우저처럼 들고 다닌다 —
 * {@link #exchange} 를 보라.
 *
 * <p>MockMvc 는 요청 사이에 세션을 잇지 않는다. 그래서 여기서 오가는 모든 요청이 이미 세션
 * 없는 요청이고, 이 클래스가 다루려는 상황이 바로 그것이다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
@ActiveProfiles("test")
class RememberMeTest {

    private static final Pattern CSRF_INPUT =
            Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private static final String REMEMBER_ME_COOKIE = "remember-me";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    /** 브라우저 자리. remember-me 토큰이 쓸 때마다 갈리므로 응답이 준 쿠키를 다음 요청에 싣는다. */
    private final Map<String, Cookie> jar = new LinkedHashMap<>();

    @BeforeEach
    void reset() {
        jar.clear();
        jdbc.execute("delete from persistent_logins");
        jdbc.execute("delete from study_log_tag");
        jdbc.execute("delete from study_log");
        jdbc.execute("delete from category");
    }

    @Test
    @DisplayName("체크박스를 켠 로그인은 쿠키와 토큰 행을 남긴다")
    void issuesTokenWhenAsked() throws Exception {
        MvcResult login = login(true);

        assertThat(login.getResponse().getCookie(REMEMBER_ME_COOKIE)).isNotNull();
        assertThat(tokenRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("체크박스 없는 로그인은 쿠키도 토큰 행도 남기지 않는다")
    void issuesNothingWhenNotAsked() throws Exception {
        MvcResult login = login(false);

        assertThat(login.getResponse().getCookie(REMEMBER_ME_COOKIE)).isNull();
        assertThat(tokenRows()).isZero();
    }

    /**
     * 이 클래스의 중심. 세션 없이 remember-me 쿠키와 CSRF 쿠키만으로 저장이 끝나는지 본다 —
     * 앱이 재기동돼 세션이 통째로 사라진 뒤 열려 있던 폼의 저장을 누르는 상황이다.
     *
     * <p>CSRF 토큰이 세션에 있었다면 여기서 {@code CsrfFilter} 가 먼저 403 을 낸다.
     * {@code FilterOrderRegistration} 의 등록 순서가 {@code CsrfFilter} → {@code LogoutFilter} →
     * {@code UsernamePasswordAuthenticationFilter} → {@code RememberMeAuthenticationFilter} 라
     * remember-me 는 그 뒤에야 차례가 오고, 그래서 remember-me 만으로는 노트를 살리지 못한다.
     * 이 테스트가 둘을 함께 못박는다.
     */
    @Test
    @DisplayName("세션 없이 remember-me 쿠키와 CSRF 쿠키만으로 저장된다")
    void savesWithoutSession() throws Exception {
        login(true);

        MvcResult form = exchange(get("/logs/new"));
        assertThat(form.getResponse().getStatus()).as("remember-me 로 쓰기 화면까지 들어와야 한다")
                .isEqualTo(200);
        assertThat(jar).containsKey(CSRF_COOKIE);

        MvcResult saved = exchange(post("/logs")
                .param("_csrf", csrfToken(form))
                .param("title", "세션 없이 저장")
                .param("studyDate", "2026-08-21")
                .param("startTime", "20:00")
                .param("endTime", "21:00")
                .param("categoryName", "Spring")
                .param("tagsCsv", "jpa")
                .param("summary", "요약")
                .param("note", "본문"));

        assertThat(saved.getResponse().getRedirectedUrl()).startsWith("/logs/");
        assertThat(jdbc.queryForObject("select count(*) from study_log", Integer.class)).isEqualTo(1);
    }

    /**
     * 여기서만 세션을 들고 다니는 것은 브라우저를 흉내 내기 위해서다. {@code LogoutFilter} 가
     * {@code RememberMeAuthenticationFilter} 보다 앞에 서므로, 세션 없이 곧장 로그아웃하면
     * 그 시점의 인증이 비어 있어 쿠키만 만료되고 토큰 행은 남는다. 화면을 한 번이라도 연
     * 뒤에는 remember-me 가 세션을 만들어 두므로 실제 경로는 이쪽이다.
     */
    @Test
    @DisplayName("로그아웃은 토큰 행을 지우고 쿠키를 만료시킨다")
    void logoutClearsToken() throws Exception {
        login(true);
        MockHttpSession session = new MockHttpSession();
        MvcResult page = exchange(get("/logs").session(session));

        MvcResult logout = exchange(post("/logout")
                .session(session)
                .param("_csrf", csrfToken(page)));

        assertThat(tokenRows()).isZero();
        assertThat(logout.getResponse().getCookie(REMEMBER_ME_COOKIE)).isNotNull();
        assertThat(logout.getResponse().getCookie(REMEMBER_ME_COOKIE).getMaxAge()).isZero();
    }

    /**
     * 같은 series 의 옛 토큰이 한 번 더 오는 상황. 탭 둘이 열려 있다가 앱이 재기동되면 한쪽이
     * 토큰을 갈아 끼운 뒤 다른 쪽이 옛 값을 들고 오고, 뒤로 가기 복원이나 재시도도 같은 모양이다.
     *
     * <p>{@code AbstractRememberMeServices.autoLogin} 은 이때 쿠키를 지운 뒤
     * {@code CookieTheftException} 을 다시 던지는데, {@code RememberMeAuthenticationFilter} 의
     * 예외 표가 {@code autoLogin} 호출을 덮지 않아 그대로 필터 밖으로 나간다. 그러면 이 사이클이
     * 없애려던 결말이 403 에서 500 으로 자리만 옮긴다.
     */
    @Test
    @DisplayName("옛 토큰이 다시 와도 500 이 아니라 로그인 화면으로")
    void replayedTokenDoesNotBecomeServerError() throws Exception {
        login(true);
        Cookie stale = jar.get(REMEMBER_ME_COOKIE);

        exchange(get("/logs"));

        jar.put(REMEMBER_ME_COOKIE, stale);
        MvcResult replayed = exchange(get("/logs/new"));

        assertThat(replayed.getResponse().getStatus()).isEqualTo(302);
        assertThat(replayed.getResponse().getRedirectedUrl()).endsWith("/login");
    }

    private MvcResult login(boolean remember) throws Exception {
        MvcResult page = exchange(get("/login"));
        MockHttpServletRequestBuilder request = post("/login")
                .param("_csrf", csrfToken(page))
                .param("username", "tester")
                .param("password", "test-password");
        if (remember) {
            request = request.param("remember-me", "on");
        }
        return exchange(request);
    }

    /** 들고 있는 쿠키를 실어 보내고, 응답이 준 쿠키로 갈아 끼운다. 만료 쿠키는 버린다. */
    private MvcResult exchange(MockHttpServletRequestBuilder request) throws Exception {
        // 빈 배열은 거부되므로 첫 요청은 쿠키 없이 나간다
        if (!jar.isEmpty()) {
            request = request.cookie(jar.values().toArray(new Cookie[0]));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                jar.remove(cookie.getName());
            } else {
                jar.put(cookie.getName(), cookie);
            }
        }
        return result;
    }

    private int tokenRows() {
        return jdbc.queryForObject("select count(*) from persistent_logins", Integer.class);
    }

    private static String csrfToken(MvcResult result) throws Exception {
        Matcher matcher = CSRF_INPUT.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("화면에 _csrf 숨은 입력이 있어야 한다").isTrue();
        return matcher.group(1);
    }
}
