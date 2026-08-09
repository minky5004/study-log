package com.minky.studylog.web;

import com.minky.studylog.service.importer.MarkdownImportService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ImportController {

    private final MarkdownImportService importService;

    public ImportController(MarkdownImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/import")
    public String form() {
        return "io/import";
    }

    /**
     * 결과를 리다이렉트로 넘기지 않고 같은 요청에서 그린다 — 실패 표는 파일명과 사유가 줄마다
     * 붙어 플래시 속성에 담기에 크고, 새로고침으로 되살아나면 이미 들어간 기록을 다시 올렸다는
     * 오해를 부른다.
     */
    @PostMapping("/import")
    public String upload(@RequestParam("files") List<MultipartFile> files, Model model) {
        model.addAttribute("report", importService.importFrom(files));
        return "io/import";
    }
}
