package com.minky.studylog.web;

import com.minky.studylog.service.StudyLogNotFoundException;
import com.minky.studylog.service.StudyLogService;
import com.minky.studylog.service.TagNormalizer;
import com.minky.studylog.web.dto.StudyLogDay;
import com.minky.studylog.web.dto.StudyLogForm;
import com.minky.studylog.web.dto.StudyLogListItem;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/logs")
public class StudyLogController {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_TAGS = 20;
    private static final int MAX_TAG_LENGTH = 50;

    /** 한 줄 입력에서 공백을 유니코드 기준으로 접는다. 노트는 줄바꿈이 뜻을 가지므로 제외. */
    private static final String[] COLLAPSED_FIELDS =
            {"title", "categoryName", "tagsCsv", "summary"};

    private final StudyLogService studyLogService;

    public StudyLogController(StudyLogService studyLogService) {
        this.studyLogService = studyLogService;
    }

    /**
     * 비분리 공백(U+00A0)만 담긴 입력은 {@code @NotBlank} 가 쓰는 {@code String.trim()} 을
     * 통과해 버린다. 검증 전에 접어서 빈 값으로 만들어 폼 단계에서 잡는다 —
     * 그대로 두면 서비스가 던지는 예외가 500 으로 나간다.
     */
    @InitBinder
    void normalizeSingleLineFields(WebDataBinder binder) {
        for (String field : COLLAPSED_FIELDS) {
            binder.registerCustomEditor(String.class, field, new PropertyEditorSupport() {
                @Override
                public void setAsText(String text) {
                    if (text == null) {
                        setValue(null);
                        return;
                    }
                    String collapsed = text.replaceAll("(?U)\\s+", " ").trim();
                    setValue(collapsed.isEmpty() ? null : collapsed);
                }
            });
        }
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        // 음수 페이지는 PageRequest 가 예외로 거부한다 — 주소창 조작이 500 이 되지 않게 접는다
        int requested = Math.max(0, page);
        Page<StudyLogListItem> logs = studyLogService.findAll(PageRequest.of(requested, PAGE_SIZE,
                // id 를 마지막 정렬 키로 둬야 날짜·시각이 같은 기록의 순서가 페이지마다 흔들리지 않는다
                Sort.by(Sort.Direction.DESC, "studyDate", "startTime", "id")));

        if (requested > 0 && requested >= logs.getTotalPages()) {
            return "redirect:/logs?page=" + Math.max(0, logs.getTotalPages() - 1);
        }

        model.addAttribute("logs", logs);
        // 묶기가 페이징 뒤에 오는 순서라 상자 합계가 그 페이지에 보이는 세션의 합과 항상 같다
        model.addAttribute("days", StudyLogDay.groupByDate(logs.getContent()));
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
        rejectInvalidTags(form, binding);

        if (binding.hasErrors()) {
            return "logs/form";
        }
        return "redirect:/logs/" + studyLogService.create(form);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("log", studyLogService.findById(id));
        return "logs/detail";
    }

    /**
     * 없는 기록은 주소창 조작뿐 아니라 삭제된 기록의 링크로도 늘 들어온다.
     * 기본 처리에 맡기면 500 스택트레이스가 나가 없는 것과 고장 난 것이 구별되지 않는다.
     */
    @ExceptionHandler(StudyLogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/4xx";
    }

    /** 태그 컬럼이 50자라, 넘는 값은 저장 시점에 터진다. 정규화한 결과로 미리 잰다. */
    private void rejectInvalidTags(StudyLogForm form, BindingResult binding) {
        Set<String> tags = TagNormalizer.normalizeAll(form.getTagsCsv());
        if (tags.size() > MAX_TAGS) {
            binding.rejectValue("tagsCsv", "tooMany", "태그는 최대 " + MAX_TAGS + "개까지 넣을 수 있습니다");
        } else if (tags.stream().anyMatch(tag -> tag.length() > MAX_TAG_LENGTH)) {
            binding.rejectValue("tagsCsv", "tooLong", "태그는 하나당 " + MAX_TAG_LENGTH + "자 이내로 적어주세요");
        }
    }
}
