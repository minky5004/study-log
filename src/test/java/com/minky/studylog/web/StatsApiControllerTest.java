package com.minky.studylog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minky.studylog.config.SecurityConfig;
import com.minky.studylog.repository.projection.CategoryTotal;
import com.minky.studylog.repository.projection.DailyTotal;
import com.minky.studylog.service.BucketTotal;
import com.minky.studylog.service.StatsService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsApiController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class StatsApiControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean StatsService statsService;

    /**
     * 기본 범위를 서버가 정하므로 그 계산이 여기서 고정된다. 러너가 UTC 라 한국 기준을 박지 않으면
     * 자정 직후에만 하루 어긋나 로컬에서는 재현되지 않는다.
     */
    @Test
    @DisplayName("히트맵 기본 범위는 오늘 포함 365일 · 한국 기준")
    void heatmapDefaultsToLastYear() throws Exception {
        mockMvc.perform(get("/api/stats/heatmap")).andExpect(status().isOk());

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        Mockito.verify(statsService).heatmap(from.capture(), to.capture());

        LocalDate today = LocalDate.now(StudyLogForm.ZONE);
        assertThat(to.getValue()).isEqualTo(today);
        assertThat(from.getValue()).isEqualTo(today.minusDays(364));
    }

    @Test
    @DisplayName("히트맵 범위는 질의 문자열로 덮어씀")
    void heatmapAcceptsExplicitRange() throws Exception {
        mockMvc.perform(get("/api/stats/heatmap")
                        .param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk());

        Mockito.verify(statsService).heatmap(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName("뒤집힌 범위는 400 — 주소창 조작이 500 이 되지 않게")
    void rejectsInvertedRange() throws Exception {
        mockMvc.perform(get("/api/stats/heatmap")
                        .param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());

        Mockito.verify(statsService, Mockito.never()).heatmap(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("추이 기본 단위는 주 · 12칸 범위")
    void trendDefaultsToTwelveWeeks() throws Exception {
        Mockito.when(statsService.weekly(Mockito.any(), Mockito.any()))
                .thenReturn(List.of(new BucketTotal("2026-08-03", 90)));

        mockMvc.perform(get("/api/stats/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bucket").value("2026-08-03"))
                .andExpect(jsonPath("$[0].totalMinutes").value(90));

        LocalDate today = LocalDate.now(StudyLogForm.ZONE);
        Mockito.verify(statsService).weekly(today.minusWeeks(11), today);
    }

    @Test
    @DisplayName("unit=month 는 월간 집계로 갈림")
    void trendSwitchesToMonthly() throws Exception {
        mockMvc.perform(get("/api/stats/trend").param("unit", "month"))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now(StudyLogForm.ZONE);
        Mockito.verify(statsService).monthly(today.minusMonths(11), today);
        Mockito.verify(statsService, Mockito.never()).weekly(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("모르는 단위는 400")
    void rejectsUnknownUnit() throws Exception {
        mockMvc.perform(get("/api/stats/trend").param("unit", "day"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("분야별 합계는 이름과 색 배정용 식별자를 함께 실음")
    void categoriesCarryIdAndName() throws Exception {
        Mockito.when(statsService.byCategory()).thenReturn(List.of(new CategoryTotal(2L, "CS", 120)));

        mockMvc.perform(get("/api/stats/categories"))
                .andExpect(jsonPath("$[0].categoryId").value(2))
                .andExpect(jsonPath("$[0].categoryName").value("CS"))
                .andExpect(jsonPath("$[0].totalMinutes").value(120));
    }

    @Test
    @DisplayName("시간대는 빈 데이터에도 24칸 — 화면이 칸을 다시 세지 않게")
    void hoursAlwaysHave24Slots() throws Exception {
        Mockito.when(statsService.byHourOfDay()).thenReturn(new int[24]);

        mockMvc.perform(get("/api/stats/hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24));
    }

    @Test
    @DisplayName("일별 합계 날짜는 ISO 문자열로 나감 — 차트가 파싱 규칙을 추측하지 않게")
    void heatmapDatesAreIsoStrings() throws Exception {
        Mockito.when(statsService.heatmap(Mockito.any(), Mockito.any()))
                .thenReturn(List.of(new DailyTotal(LocalDate.of(2026, 8, 3), 120)));

        mockMvc.perform(get("/api/stats/heatmap"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"2026-08-03\"")))
                .andExpect(jsonPath("$[0].totalMinutes").value(120));
    }
}
