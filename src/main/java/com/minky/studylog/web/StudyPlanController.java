package com.minky.studylog.web;

import com.minky.studylog.domain.PlanPriority;
import com.minky.studylog.service.StudyPlanNotFoundException;
import com.minky.studylog.service.StudyPlanService;
import com.minky.studylog.web.dto.StudyPlanForm;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
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
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 화면이 한 장뿐이라 추가 폼도 목록 안에 둔다. 그래서 {@code SecurityConfig} 를 건드리지 않는다 —
 * 거기서 경로를 따로 세우는 것은 <b>쓰기 화면이 GET 일 때</b>뿐이고, 여기 쓰기는 전부 POST 라
 * 메서드 규칙에 이미 걸린다.
 */
@Controller
@RequestMapping("/plans")
public class StudyPlanController {

    /** 한 줄 입력에서 공백을 유니코드 기준으로 접는다 — {@code StudyLogController} 와 같은 이유. */
    private static final String[] COLLAPSED_FIELDS = {"title", "note"};

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    /**
     * 비분리 공백(U+00A0)만 담긴 입력은 {@code @NotBlank} 가 쓰는 {@code String.trim()} 을
     * 통과한다. 검증 전에 접어 빈 값으로 만들어 폼 단계에서 잡는다 — 그대로 두면 도메인이
     * 던지는 예외가 500 으로 나간다.
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
    public String list(Model model) {
        model.addAttribute("form", new StudyPlanForm());
        return listView(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StudyPlanForm form, BindingResult binding,
                         Model model) {
        if (binding.hasErrors()) {
            return listView(model);
        }
        studyPlanService.create(form);
        return "redirect:/plans";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        studyPlanService.toggle(id);
        return "redirect:/plans";
    }

    /**
     * 삭제에 확인 화면을 두지 않는다. 기록은 수천 자 노트를 잃을 수 있어 한 단계를 세웠지만
     * 여기서 사라지는 것은 한 줄이라, 확인 화면의 마찰이 잃는 것보다 크다.
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        studyPlanService.delete(id);
        return "redirect:/plans";
    }

    /**
     * 두 창에서 같은 항목을 지우면 뒤엣것이 없는 id 로 들어온다. 기본 처리에 맡기면 500 이 나가
     * 이미 사라진 것과 고장 난 것이 구별되지 않는다.
     */
    @ExceptionHandler(StudyPlanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/4xx";
    }

    /** 목록 뷰는 반드시 여기로 돌려준다 — 검증 실패 복귀도 같은 화면이라 한 곳이 아니면 샌다. */
    private String listView(Model model) {
        model.addAttribute("pending", studyPlanService.findPending());
        model.addAttribute("done", studyPlanService.findDone());
        model.addAttribute("priorities", PlanPriority.values());
        return "plans/index";
    }
}
