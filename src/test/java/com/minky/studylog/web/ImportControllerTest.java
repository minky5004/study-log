package com.minky.studylog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.minky.studylog.config.SecurityConfig;
import com.minky.studylog.service.importer.ImportReport;
import com.minky.studylog.service.importer.ImportReport.Failure;
import com.minky.studylog.service.importer.MarkdownImportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class ImportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean MarkdownImportService importService;

    @Test
    @DisplayName("가져오기 화면은 결과 없이 폼만")
    void formHasNoReport() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("io/import"))
                .andExpect(model().attributeDoesNotExist("report"));
    }

    /**
     * 실패는 건수가 아니라 <b>사유</b>가 화면에 닿아야 쓸모가 있다 — 무엇을 고쳐 다시 올릴지
     * 알려 주는 것이 이 화면의 목적이다.
     */
    @Test
    @DisplayName("업로드 결과의 실패 사유가 화면에 나감")
    void uploadRendersFailureReasons() throws Exception {
        BDDMockito.given(importService.importFrom(ArgumentMatchers.anyList()))
                .willReturn(new ImportReport(2, 1,
                        List.of(new Failure("a.md", "프론트매터 없음 — 첫 줄이 --- 이어야 합니다"))));

        mockMvc.perform(multipart("/import").file(new MockMultipartFile("files", "a.md",
                        "text/markdown", "x".getBytes(StandardCharsets.UTF_8))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("io/import"))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("a.md"),
                        Matchers.containsString("프론트매터 없음"))));
    }
}
