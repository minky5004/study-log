package com.minky.studylog.web.dto;

import com.minky.studylog.domain.PlanPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 할 일 추가 폼. 수정 화면이 없으므로 {@code id} 도 갱신 경로도 두지 않는다 —
 * 한 줄짜리를 고치는 것은 지우고 다시 적는 것과 비용이 같다.
 */
public class StudyPlanForm {

    @NotBlank(message = "할 일은 필수")
    @Size(max = 200, message = "할 일은 200자 이내")
    private String title;

    @Size(max = 200, message = "한 줄 설명은 200자 이내")
    private String note;

    /** 고르지 않고 넘긴 경우의 자리. 셋 중 가운데가 기본이라 대다수 입력이 무선택으로 끝난다. */
    private PlanPriority priority = PlanPriority.NORMAL;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public PlanPriority getPriority() {
        return priority;
    }

    public void setPriority(PlanPriority priority) {
        this.priority = priority;
    }
}
