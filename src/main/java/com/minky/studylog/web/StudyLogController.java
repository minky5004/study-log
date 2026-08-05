package com.minky.studylog.web;

import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.web.dto.StudyLogForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/logs")
public class StudyLogController {

    private static final int PAGE_SIZE = 20;

    private final StudyLogService studyLogService;

    public StudyLogController(StudyLogService studyLogService) {
        this.studyLogService = studyLogService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", studyLogService.findAll(
                PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "studyDate", "startTime"))));
        return "logs/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new StudyLogForm());
        return "logs/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StudyLogForm form, BindingResult binding) {
        // 같은 시각은 0분과 24시간을 구별할 수 없어 도메인이 거부한다. 예외로 튀기 전에 폼에서 잡는다
        if (form.getStartTime() != null && form.getStartTime().equals(form.getEndTime())) {
            binding.rejectValue("endTime", "sameTime", "시작 시각과 종료 시각이 같을 수 없습니다");
        }
        if (binding.hasErrors()) {
            return "logs/form";
        }
        studyLogService.create(form);
        return "redirect:/logs";
    }
}
