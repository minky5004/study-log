package com.minky.studylog.web;

import com.minky.studylog.service.export.MarkdownExportService;
import com.minky.studylog.web.dto.StudyLogForm;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Controller
public class ExportController {

    private final MarkdownExportService exportService;

    public ExportController(MarkdownExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * 백업 내려받기. GET 이지만 인증을 건다 — 읽기 공개의 바닥값은 화면 단위 조회를 두고 세운
     * 것이고, 이 주소는 한 번의 요청이 DB 전량을 흘려보낸다. 목록·상세로도 같은 데이터를 볼 수
     * 있다는 점은 같지만 그쪽은 페이지 단위다.
     *
     * <p>파일명에 날짜를 박는다 — 여러 번 내려받은 백업이 내려받기 폴더에서
     * {@code (1)} {@code (2)} 로만 갈리면 어느 것이 최신인지 알 수 없다.
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export() {
        String fileName = "study-log-" + LocalDate.now(StudyLogForm.ZONE) + ".zip";
        StreamingResponseBody body = exportService::writeZip;

        return ResponseEntity.ok()
                .contentType(new MediaType("application", "zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
