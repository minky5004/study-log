package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.PlanPriority;
import com.minky.studylog.domain.StudyPlan;
import com.minky.studylog.repository.StudyPlanRepository;
import com.minky.studylog.web.dto.StudyPlanForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정렬 규칙이 이 클래스의 본체다. 우선순위가 {@code varchar} 로 저장돼 SQL 정렬이 뜻과
 * 어긋나므로 순서를 자바가 지는데, 그 결정이 지켜지는지는 여기서만 드러난다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudyPlanServiceTest {

    @Autowired StudyPlanService studyPlanService;
    @Autowired StudyPlanRepository studyPlanRepository;

    /**
     * 알파벳순이면 {@code HIGH · LOW · NORMAL} 이 된다 — 세 우선순위를 <b>알파벳과 뜻이 갈리는
     * 순서로</b> 넣어 두 규칙이 같은 답을 내지 않게 한다. 같은 순서로 넣으면 SQL 정렬로
     * 되돌려도 통과한다.
     */
    @Test
    @DisplayName("미완료는 우선순위 순 — 알파벳순으로 되돌리면 깨지는 배치")
    void ordersPendingByPriority() {
        save("낮은 것", PlanPriority.LOW);
        save("보통 것", PlanPriority.NORMAL);
        save("높은 것", PlanPriority.HIGH);

        assertThat(studyPlanService.findPending())
                .extracting(StudyPlan::getTitle)
                .containsExactly("높은 것", "보통 것", "낮은 것");
    }

    @Test
    @DisplayName("같은 우선순위는 먼저 적은 것 먼저 — 새로고침마다 체크박스 자리가 바뀌지 않게")
    void keepsInsertionOrderWithinSamePriority() {
        save("먼저", PlanPriority.NORMAL);
        save("나중", PlanPriority.NORMAL);

        assertThat(studyPlanService.findPending())
                .extracting(StudyPlan::getTitle)
                .containsExactly("먼저", "나중");
    }

    @Test
    @DisplayName("체크하면 미완료에서 빠지고 완료로 — 다시 누르면 되돌아옴")
    void movesBetweenPendingAndDone() {
        Long id = save("옵시디언 활용법", PlanPriority.NORMAL);

        studyPlanService.toggle(id);
        assertThat(studyPlanService.findPending()).isEmpty();
        assertThat(studyPlanService.findDone()).extracting(StudyPlan::getTitle)
                .containsExactly("옵시디언 활용법");

        studyPlanService.toggle(id);
        assertThat(studyPlanService.findDone()).isEmpty();
        assertThat(studyPlanService.findPending()).hasSize(1);
    }

    @Test
    @DisplayName("완료 시각은 체크와 함께 붙고 해제와 함께 사라짐")
    void keepsCompletedAtInStepWithDone() {
        Long id = save("강의 계속 보기", PlanPriority.NORMAL);

        studyPlanService.toggle(id);
        assertThat(studyPlanRepository.findById(id).orElseThrow().getCompletedAt()).isNotNull();

        studyPlanService.toggle(id);
        assertThat(studyPlanRepository.findById(id).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("없는 항목의 토글·삭제는 전용 예외 — 조용히 넘기면 눌린 것으로 읽힘")
    void rejectsMissingId() {
        assertThatThrownBy(() -> studyPlanService.toggle(404L))
                .isInstanceOf(StudyPlanNotFoundException.class);
        assertThatThrownBy(() -> studyPlanService.delete(404L))
                .isInstanceOf(StudyPlanNotFoundException.class);
    }

    @Test
    @DisplayName("삭제는 목록에서 사라짐")
    void deletesPlan() {
        Long id = save("주식 게임", PlanPriority.LOW);

        studyPlanService.delete(id);

        assertThat(studyPlanService.findPending()).isEmpty();
    }

    /** 폼을 거쳐 만든다 — 컨트롤러가 지나는 길과 같은 경로여야 기본값 처리도 함께 덮인다. */
    private Long save(String title, PlanPriority priority) {
        StudyPlanForm form = new StudyPlanForm();
        form.setTitle(title);
        form.setPriority(priority);
        return studyPlanService.create(form);
    }

    /**
     * 나머지 전부가 기대는 전제라 따로 세운다. 목록이 전량 조회라 앞 테스트의 행이 남으면
     * {@code containsExactly} 단언들이 통째로 흔들리고, 그 실패는 정렬 결함처럼 보인다.
     */
    @Test
    @DisplayName("롤백 위에서 돈다 — 앞 테스트의 행이 남지 않음")
    void startsEmpty() {
        assertThat(studyPlanRepository.findAll()).isEmpty();
    }
}
