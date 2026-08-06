package com.minky.studylog.service;

/**
 * 요청한 기록이 없을 때. 전용 타입을 두는 이유는 {@code NoSuchElementException} 이
 * {@code Optional.get()} · {@code Iterator.next()} 도 던지는 흔한 예외이기 때문 —
 * 그 타입으로 404 를 매기면 다른 경로의 진짜 서버 결함이 "없는 기록"으로 둔갑하고
 * 500 이 나가야 할 자리에 404 가 기록된다.
 */
public class StudyLogNotFoundException extends RuntimeException {

    public StudyLogNotFoundException(Long id) {
        super("기록 없음: " + id);
    }
}
