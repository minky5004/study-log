package com.minky.studylog.service;

/**
 * 요청한 할 일이 없을 때. 전용 타입을 두는 근거는 {@link StudyLogNotFoundException} 과 같다 —
 * 흔한 예외 타입에 404 를 매기면 다른 경로의 진짜 결함이 "없는 항목" 으로 둔갑한다.
 */
public class StudyPlanNotFoundException extends RuntimeException {

    public StudyPlanNotFoundException(Long id) {
        super("할 일 없음: " + id);
    }
}
