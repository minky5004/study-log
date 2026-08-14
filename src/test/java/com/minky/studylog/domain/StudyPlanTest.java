package com.minky.studylog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyPlanTest {

    // 눈에 보이지 않는 문자라 소스에 그대로 넣지 않는다
    private static final String NBSP = Character.toString(0x00A0);

    @Test
    @DisplayName("제목은 trim · 연속 공백 1칸 — 붙여넣은 비분리 공백까지")
    void collapsesTitleWhitespace() {
        StudyPlan plan = new StudyPlan("  옵시디언" + NBSP + "  활용법  ", null, PlanPriority.HIGH);

        assertThat(plan.getTitle()).isEqualTo("옵시디언 활용법");
    }

    @Test
    @DisplayName("공백뿐인 제목은 거부 — 화면에 이름 없는 할 일이 생기지 않게")
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new StudyPlan(NBSP, null, PlanPriority.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudyPlan(null, null, PlanPriority.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 빈 문자열로 두면 템플릿의 존재 검사를 통과해 빈 줄이 렌더링된다. */
    @Test
    @DisplayName("빈 한 줄은 null — 화면에서 줄 자체가 사라지도록")
    void turnsEmptyNoteIntoNull() {
        assertThat(new StudyPlan("강의 계속 보기", "   ", PlanPriority.LOW).getNote()).isNull();
        assertThat(new StudyPlan("강의 계속 보기", null, PlanPriority.LOW).getNote()).isNull();
    }

    @Test
    @DisplayName("우선순위 미지정은 보통")
    void defaultsToNormalPriority() {
        assertThat(new StudyPlan("공부 메모장", null, null).getPriority())
                .isEqualTo(PlanPriority.NORMAL);
    }

    /**
     * 둘이 갈라진 행은 예외가 아니라 화면의 빈칸으로 나가므로 단언을 한 곳에 묶는다.
     * 같은 불변식이 {@code ck_study_plan_completed_at} 에도 있다.
     */
    @Test
    @DisplayName("체크는 완료 여부와 완료 시각을 함께 뒤집음 — 해제하면 시각도 사라짐")
    void togglesDoneAndCompletedAtTogether() {
        StudyPlan plan = new StudyPlan("클로드 md 다듬기", null, PlanPriority.NORMAL);
        assertThat(plan.isDone()).isFalse();
        assertThat(plan.getCompletedAt()).isNull();

        plan.toggle();
        assertThat(plan.isDone()).isTrue();
        assertThat(plan.getCompletedAt()).isNotNull();

        plan.toggle();
        assertThat(plan.isDone()).isFalse();
        assertThat(plan.getCompletedAt()).isNull();
    }

    /** 선언 순서가 정렬 순서라는 것이 서비스 정렬의 전제다 — 순서를 바꾸면 여기서 먼저 걸린다. */
    @Test
    @DisplayName("우선순위 자연 순서는 높음 · 보통 · 낮음")
    void ordersPriorityByDeclaration() {
        assertThat(PlanPriority.values())
                .containsExactly(PlanPriority.HIGH, PlanPriority.NORMAL, PlanPriority.LOW);
        assertThat(PlanPriority.HIGH.compareTo(PlanPriority.LOW)).isNegative();
    }
}
