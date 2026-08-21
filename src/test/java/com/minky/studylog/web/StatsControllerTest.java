package com.minky.studylog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.minky.studylog.config.SecurityConfig;
import org.hamcrest.Matchers;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
@Import(SecurityConfig.class)
// SecurityConfig 가 remember-me 토큰 저장소를 함께 들고 오는데 화면 슬라이스에는 DB 가 없다.
// 여기서 재는 것은 권한 경계와 화면이지 토큰이 어디에 담기는가가 아니다 — 실제 저장소를
// 밟는 곳은 RememberMeTest 하나다
@MockitoBean(types = DataSource.class)
@ActiveProfiles("test")
class StatsControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("통계 화면은 비로그인도 열림")
    void statsPageIsPublic() throws Exception {
        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(view().name("stats/index"));
    }

    /**
     * 차트는 스크립트가 그리므로 카드가 통째로 사라져도 응답은 200 이다. 스크립트가 찾는
     * 자리를 여기서 고정해 둔다 — 이름이 어긋나면 화면에서만, 그것도 조용히 드러난다.
     */
    @Test
    @DisplayName("네 카드의 스크립트 진입점이 모두 실려 나감")
    void pageCarriesEveryChartHook() throws Exception {
        mockMvc.perform(get("/stats")).andExpect(content().string(Matchers.allOf(
                Matchers.containsString("data-chart=\"heatmap\""),
                Matchers.containsString("data-chart=\"trend\""),
                Matchers.containsString("data-chart=\"categories\""),
                Matchers.containsString("data-chart=\"hours\""))));
    }
}
