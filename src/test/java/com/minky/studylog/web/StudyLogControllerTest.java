package com.minky.studylog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.minky.studylog.config.SecurityConfig;
import com.minky.studylog.service.StudyLogNotFoundException;
import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.web.dto.StudyLogDetail;
import com.minky.studylog.web.dto.StudyLogForm;
import com.minky.studylog.web.dto.StudyLogListItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 슬라이스는 SecurityConfig 를 자동으로 포함하지 않아 기본 자동 구성이 401 을 낸다.
// 필터를 끄는 대신 실제 필터체인을 가져와, 권한 분리(Task 11) 시점에 이 테스트가 함께 반응하게 한다
@WebMvcTest(StudyLogController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class StudyLogControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean StudyLogService studyLogService;

    @BeforeEach
    void setUp() {
        Mockito.when(studyLogService.findAll(any(Pageable.class))).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("폼은 오늘 날짜가 채워진 채로 열림 — 호스트 시간대와 무관하게 한국 기준")
    void formPrefillsToday() throws Exception {
        // LocalDate.now() 를 그대로 쓰면 UTC 러너에서 KST 와 하루 갈린다
        mockMvc.perform(get("/logs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(model().attribute("form",
                        hasProperty("studyDate", is(LocalDate.now(StudyLogForm.ZONE)))));
    }

    @Test
    @DisplayName("제목 없이 제출하면 폼으로 되돌아감 · 저장하지 않음")
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/logs").param("title", "").param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "10:00")
                        .param("categoryName", "Spring").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"));

        Mockito.verify(studyLogService, Mockito.never()).create(any());
    }

    @Test
    @DisplayName("시작·종료가 같으면 폼 단계에서 거부 — 도메인 예외로 튀기 전에")
    void rejectsSameStartAndEnd() throws Exception {
        mockMvc.perform(post("/logs").param("title", "같은 시각").param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "09:00")
                        .param("categoryName", "Spring").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(model().attributeHasFieldErrors("form", "endTime"));

        Mockito.verify(studyLogService, Mockito.never()).create(any());
    }

    @Test
    @DisplayName("정상 입력은 저장 후 그 기록의 상세로 리다이렉트 — 방금 쓴 노트를 바로 확인")
    void redirectsToDetailAfterCreate() throws Exception {
        Mockito.when(studyLogService.create(any())).thenReturn(7L);

        mockMvc.perform(post("/logs").param("title", "트랜잭션 격리 수준")
                        .param("studyDate", "2026-08-03")
                        .param("startTime", "23:00").param("endTime", "01:00")
                        .param("categoryName", "Spring").param("tagsCsv", "jpa").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logs/7"));

        Mockito.verify(studyLogService).create(any());
    }

    @Test
    @DisplayName("상세는 렌더링된 노트를 그대로 출력 — 새니타이즈를 거친 HTML 이라 이스케이프하지 않는다")
    void detailRendersNoteHtml() throws Exception {
        Mockito.when(studyLogService.findById(1L)).thenReturn(detail("<h1>큐</h1>"));

        mockMvc.perform(get("/logs/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/detail"))
                .andExpect(content().string(containsString("<h1>큐</h1>")))
                .andExpect(content().string(containsString("자료 구조 복습")))
                .andExpect(content().string(containsString("#큐")))
                .andExpect(content().string(containsString("1시간 20분")));
    }

    @Test
    @DisplayName("없는 기록은 404 — 주소창 조작에 500 스택트레이스를 내지 않게")
    void detailReturnsNotFound() throws Exception {
        Mockito.when(studyLogService.findById(999L))
                .thenThrow(new StudyLogNotFoundException(999L));

        mockMvc.perform(get("/logs/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/4xx"));
    }

    @Test
    @DisplayName("목록의 세션 제목은 상세로 가는 링크 — 없으면 상세에 닿을 경로가 없다")
    void listLinksToDetail() throws Exception {
        StudyLogListItem item = item("트랜잭션 격리 수준", LocalTime.of(23, 0), 120);
        Mockito.when(studyLogService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/logs/1\"")));
    }

    private StudyLogDetail detail(String noteHtml) {
        return new StudyLogDetail(1L, "자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(14, 0), LocalTime.of(15, 20), 80, "CS", 2L,
                List.of("자료 구조", "큐"), "큐와 스택 정리", noteHtml);
    }

    @Test
    @DisplayName("기록이 없어도 목록 화면은 열림")
    void listRendersWhenEmpty() throws Exception {
        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/list"))
                .andExpect(model().attributeExists("logs"));
    }

    @Test
    @DisplayName("기록이 있는 목록도 끝까지 렌더 — 행 안쪽 표현식 오류를 잡기 위해")
    void listRendersRows() throws Exception {
        StudyLogListItem item = new StudyLogListItem(1L, "트랜잭션 격리 수준",
                LocalDate.of(2026, 8, 3), LocalTime.of(23, 0), LocalTime.of(1, 0), 120,
                "Spring", 1L, List.of("jpa", "트랜잭션"), "격리 수준 4단계 정리");
        Mockito.when(studyLogService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("트랜잭션 격리 수준")))
                .andExpect(content().string(containsString("8월 3일 (월)")))
                .andExpect(content().string(containsString("2시간")))
                .andExpect(content().string(containsString("cat-" + item.categoryColorIndex())));
    }

    @Test
    @DisplayName("같은 날 두 건은 상자 하나 · 머리에 합계")
    void listGroupsSameDayIntoOneBox() throws Exception {
        Mockito.when(studyLogService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(item("밤 세션", LocalTime.of(21, 0), 90), item("낮 세션", LocalTime.of(14, 0), 80)),
                PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("days", hasSize(1)))
                .andExpect(content().string(containsString("2시간 50분")));
    }

    private StudyLogListItem item(String title, LocalTime start, int minutes) {
        return new StudyLogListItem(1L, title, LocalDate.of(2026, 8, 3), start,
                start.plusMinutes(minutes), minutes, "Spring", 1L, List.of("jpa"), "요약");
    }

    @Test
    @DisplayName("음수 페이지는 첫 페이지로 — 주소창 조작에 500 을 내지 않게")
    void clampsNegativePage() throws Exception {
        mockMvc.perform(get("/logs").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/list"));
    }

    @Test
    @DisplayName("마지막 페이지를 넘어선 요청은 마지막 페이지로 리다이렉트")
    void redirectsPageBeyondLast() throws Exception {
        Mockito.when(studyLogService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 20), 20));

        mockMvc.perform(get("/logs").param("page", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logs?page=0"));
    }

    @Test
    @DisplayName("50자를 넘는 태그는 폼에서 거부 — 컬럼 길이를 넘겨 저장에서 터지기 전에")
    void rejectsOverlongTag() throws Exception {
        mockMvc.perform(post("/logs").param("title", "제목").param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "10:00")
                        .param("categoryName", "Spring")
                        .param("tagsCsv", "a".repeat(51)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(model().attributeHasFieldErrors("form", "tagsCsv"));

        Mockito.verify(studyLogService, Mockito.never()).create(any());
    }

    @Test
    @DisplayName("태그 개수 상한 초과도 폼에서 거부")
    void rejectsTooManyTags() throws Exception {
        String many = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "태그" + i).collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/logs").param("title", "제목").param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "10:00")
                        .param("categoryName", "Spring")
                        .param("tagsCsv", many).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "tagsCsv"));
    }

    @Test
    @DisplayName("비분리 공백뿐인 분야·제목은 폼에서 거부 — 서비스까지 내려가 500 이 되지 않게")
    void rejectsUnicodeBlankFields() throws Exception {
        String nbsp = Character.toString(0x00A0);

        mockMvc.perform(post("/logs").param("title", nbsp).param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "10:00")
                        .param("categoryName", nbsp).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(model().attributeHasFieldErrors("form", "title", "categoryName"));

        Mockito.verify(studyLogService, Mockito.never()).create(any());
    }

    @Test
    @DisplayName("수정 폼은 저장된 값이 채워진 채로 열림")
    void editFormPrefills() throws Exception {
        Mockito.when(studyLogService.toForm(1L)).thenReturn(prefilled());

        mockMvc.perform(get("/logs/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(model().attribute("form", hasProperty("title", is("자료 구조 복습"))))
                // 폼 하나가 생성·수정을 겸하므로 제출 주소가 갈리는 지점
                .andExpect(content().string(containsString("action=\"/logs/1\"")));
    }

    @Test
    @DisplayName("없는 기록의 수정 폼은 404")
    void editFormReturnsNotFound() throws Exception {
        Mockito.when(studyLogService.toForm(999L)).thenThrow(new StudyLogNotFoundException(999L));

        mockMvc.perform(get("/logs/999/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/4xx"));
    }

    @Test
    @DisplayName("수정 제출은 그 기록의 상세로 리다이렉트")
    void updateRedirectsToDetail() throws Exception {
        mockMvc.perform(post("/logs/1").param("title", "트랜잭션 격리 수준")
                        .param("studyDate", "2026-08-03")
                        .param("startTime", "23:00").param("endTime", "01:00")
                        .param("categoryName", "Spring").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logs/1"));

        Mockito.verify(studyLogService).update(Mockito.eq(1L), any());
    }

    @Test
    @DisplayName("수정 실패로 되돌아온 폼도 제출 주소를 유지 — 경로의 id 는 요청 본문에서 오지 않는다")
    void rejectedUpdateKeepsFormAction() throws Exception {
        mockMvc.perform(post("/logs/1").param("title", "").param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "10:00")
                        .param("categoryName", "Spring").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(content().string(containsString("action=\"/logs/1\"")));

        Mockito.verify(studyLogService, Mockito.never()).update(any(), any());
    }

    @Test
    @DisplayName("삭제는 확인 화면을 먼저 거침 — GET 으로는 지워지지 않음")
    void deleteRequiresConfirmation() throws Exception {
        Mockito.when(studyLogService.findById(1L)).thenReturn(detail("<p>큐</p>"));

        mockMvc.perform(get("/logs/1/delete"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/delete-confirm"))
                .andExpect(content().string(containsString("자료 구조 복습")));

        Mockito.verify(studyLogService, Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("확인 화면의 제출만 실제 삭제 · 목록으로 리다이렉트")
    void deleteRemovesAndRedirectsToList() throws Exception {
        mockMvc.perform(post("/logs/1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logs"));

        Mockito.verify(studyLogService).delete(1L);
    }

    @Test
    @DisplayName("상세에 수정·삭제로 가는 경로가 있음")
    void detailLinksToEditAndDelete() throws Exception {
        Mockito.when(studyLogService.findById(1L)).thenReturn(detail("<p>큐</p>"));

        mockMvc.perform(get("/logs/1"))
                .andExpect(content().string(containsString("href=\"/logs/1/edit\"")))
                .andExpect(content().string(containsString("href=\"/logs/1/delete\"")));
    }

    private StudyLogForm prefilled() {
        StudyLogForm form = new StudyLogForm();
        form.setId(1L);
        form.setTitle("자료 구조 복습");
        form.setStudyDate(LocalDate.of(2026, 8, 6));
        form.setStartTime(LocalTime.of(14, 0));
        form.setEndTime(LocalTime.of(15, 20));
        form.setCategoryName("CS");
        form.setTagsCsv("자료 구조, 큐");
        form.setSummary("큐와 스택 정리");
        form.setNote("# 큐");
        return form;
    }

    @Test
    @DisplayName("노트의 줄바꿈은 보존 — 마크다운이 한 줄로 뭉개지지 않게")
    void keepsNoteLineBreaks() throws Exception {
        Mockito.when(studyLogService.create(any())).thenReturn(1L);

        mockMvc.perform(post("/logs").param("title", "제목").param("studyDate", "2026-08-03")
                        .param("startTime", "09:00").param("endTime", "10:00")
                        .param("categoryName", "Spring")
                        .param("note", "# 제목\n\n- 항목").with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<StudyLogForm> captor = ArgumentCaptor.forClass(StudyLogForm.class);
        Mockito.verify(studyLogService).create(captor.capture());
        assertThat(captor.getValue().getNote()).isEqualTo("# 제목\n\n- 항목");
    }
}
