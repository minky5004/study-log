package com.minky.studylog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 레이아웃이 모든 화면에 실어 보내는 것들. 화면별 테스트에 흩어 놓으면 어느 화면이 무엇을
 * 지키고 있는지 한눈에 볼 수 없어 {@link SecurityAccessTest} 와 같은 이유로 한 파일에 모은다.
 *
 * <p>애너테이션 조합을 {@code SecurityAccessTest} 와 같게 맞추는 것은 컨텍스트 캐시 키가 같아야
 * 앱을 한 번만 띄우기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LayoutTest {

    @Autowired MockMvc mockMvc;

    private static final String REPO_LINK = "github.com/minky5004/study-log";

    /** 홈의 lead 와 같은 문장. 카드에 뜨는 소개와 화면에 보이는 소개가 갈리지 않게. */
    private static final String LEAD_SENTENCE = "공부 세션을 기록하고 통계로 보는 로그북";

    /**
     * 배포 URL 을 받은 사람이 코드로 갈 수 있는 유일한 자리라, 프래그먼트가 빠지면 배포본과
     * 리포가 다시 끊긴다. 끊긴 것은 화면에서 표시가 나지 않는다 — 없는 채로도 200 이다.
     */
    @Test
    @DisplayName("모든 화면에 리포 링크 푸터 — 한 화면만 보면 빠진 화면을 못 잡는다")
    void carriesRepoLinkOnEveryPage() throws Exception {
        for (String path : new String[] {"/", "/logs", "/plans", "/stats", "/login"}) {
            mockMvc.perform(get(path))
                    .andExpect(content().string(Matchers.containsString(REPO_LINK)));
        }
    }

    /**
     * 링크를 붙였을 때 뜨는 카드의 재료. 설명 문구가 홈의 lead 와 같은 문장인지도 함께 본다 —
     * 한쪽만 고치면 화면과 카드가 다른 소개를 말한다.
     *
     * <p>절대 URL 이 필요한 {@code og:url} · {@code og:image} 를 두지 않기로 한 것도 여기서
     * 못박는다. 넣는 순간 배포 호스트가 마크업에 박혀 로컬·컨테이너 실행에서 어긋난다.
     */
    @Test
    @DisplayName("공유 메타는 넷 · 절대 URL 이 필요한 둘은 없음")
    void carriesShareMetaWithoutAbsoluteUrls() throws Exception {
        mockMvc.perform(get("/")).andExpect(content().string(Matchers.allOf(
                // 값까지 함께 보는 것은 홈의 lead 에도 같은 문장이 있어, 문장만 찾으면 메타가
                // 통째로 빠진 응답도 통과하기 때문
                Matchers.containsString(
                        "name=\"description\" content=\"" + LEAD_SENTENCE + "\""),
                Matchers.containsString(
                        "property=\"og:description\" content=\"" + LEAD_SENTENCE + "\""),
                Matchers.containsString("property=\"og:type\""),
                Matchers.containsString("property=\"og:site_name\""),
                Matchers.containsString("property=\"og:title\""),
                Matchers.containsString("property=\"og:description\""),
                // 태그 자체를 찾는다. 이름만 찾으면 두지 않은 이유를 적어 둔 주석에 걸린다 —
                // 주석도 응답에 실려 나가므로 그것까지 "있다" 로 세게 된다
                Matchers.not(Matchers.containsString("property=\"og:url\"")),
                Matchers.not(Matchers.containsString("property=\"og:image\"")))));
    }
}
