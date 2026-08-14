package com.minky.studylog.service;

import com.minky.studylog.domain.StudyPlan;
import com.minky.studylog.repository.StudyPlanRepository;
import com.minky.studylog.web.dto.StudyPlanForm;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {

    /**
     * 우선순위 먼저, 같으면 먼저 적은 것 먼저. 뒤 키가 있어야 같은 우선순위의 순서가 조회마다
     * 흔들리지 않는다 — 체크박스 자리가 새로고침에 바뀌면 오클릭이 난다.
     *
     * <p>뒤 키가 {@code createdAt} 이 아니라 {@code id} 인 것은 <b>한 번에 여러 줄을 적을 때</b>
     * 두 시각이 벽시계 해상도 안에 들어와 같은 값이 되기 때문이다. 그때 비교는 0 을 내고
     * 순서는 DB 가 돌려준 대로 남는다 — 목록 화면이 날짜·시각 동률에서 겪은 것과 같은 자리.
     */
    private static final Comparator<StudyPlan> PENDING_ORDER =
            Comparator.comparing(StudyPlan::getPriority).thenComparing(StudyPlan::getId);

    private final StudyPlanRepository studyPlanRepository;

    public StudyPlanService(StudyPlanRepository studyPlanRepository) {
        this.studyPlanRepository = studyPlanRepository;
    }

    @Transactional
    public Long create(StudyPlanForm form) {
        StudyPlan plan = new StudyPlan(form.getTitle(), form.getNote(), form.getPriority());
        return studyPlanRepository.save(plan).getId();
    }

    /**
     * 정렬을 SQL 이 아니라 여기서 지는 이유는 {@code StudyPlanRepository#findByDoneFalse()} 에.
     * 전량을 실어 오는 것이 값싼 것은 할 일이 한 사람 몫이라 상한이 낮기 때문 — 그 전제가
     * 깨지는 날(공유·다중 사용자)에는 정렬과 함께 페이징도 다시 본다.
     */
    @Transactional(readOnly = true)
    public List<StudyPlan> findPending() {
        return studyPlanRepository.findByDoneFalse().stream().sorted(PENDING_ORDER).toList();
    }

    @Transactional(readOnly = true)
    public List<StudyPlan> findDone() {
        return studyPlanRepository.findByDoneTrueOrderByCompletedAtDesc();
    }

    @Transactional
    public void toggle(Long id) {
        find(id).toggle();
    }

    @Transactional
    public void delete(Long id) {
        studyPlanRepository.delete(find(id));
    }

    /**
     * 없는 id 를 조용히 넘기지 않는다. 토글·삭제는 화면의 폼이 내는 요청이라, 아무 일도
     * 일어나지 않고 목록으로 되돌아가면 사용자는 눌린 것으로 읽는다.
     */
    private StudyPlan find(Long id) {
        return studyPlanRepository.findById(id).orElseThrow(() -> new StudyPlanNotFoundException(id));
    }
}
