package com.minky.studylog.repository;

import com.minky.studylog.domain.StudyPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    /**
     * 미완료 전량. <b>여기서 정렬하지 않는다</b> — 우선순위가 {@code varchar} 로 저장돼
     * {@code order by priority} 가 {@code HIGH, LOW, NORMAL} 순으로 붙는다. 뜻대로 세우려면
     * {@code case when} 이 필요한데, 그러면 우선순위 순서가 enum 과 SQL 두 곳에 생긴다.
     * 정렬은 {@code StudyPlanService} 가 자바로 진다.
     */
    List<StudyPlan> findByDoneFalse();

    /** 완료는 최근 것부터. 이쪽은 정렬 키가 시각 하나라 SQL 로 끝난다. */
    List<StudyPlan> findByDoneTrueOrderByCompletedAtDesc();
}
