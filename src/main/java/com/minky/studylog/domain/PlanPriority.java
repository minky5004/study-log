package com.minky.studylog.domain;

/**
 * 할 일의 우선순위. <b>선언 순서가 곧 정렬 순서</b>라 목록 정렬이 자연 순서 비교 하나로 끝난다.
 *
 * <p>저장은 {@code STRING} 이다. {@code ORDINAL} 은 상수를 사이에 하나 끼우는 순간 이미 저장된
 * 행의 뜻이 통째로 밀리는데, 그 사고는 컴파일도 테스트도 통과하고 화면에서만 조용히 드러난다.
 *
 * <p>대신 SQL {@code order by} 로는 정렬하지 못한다 — 문자열이라 {@code HIGH, LOW, NORMAL} 순으로
 * 붙는다. 정렬을 서비스가 자바로 지는 이유가 이것이고, 할 일이 한 사람 몫이라 그래도 값싸다.
 */
public enum PlanPriority {

    HIGH("높음"),
    NORMAL("보통"),
    LOW("낮음");

    private final String label;

    PlanPriority(String label) {
        this.label = label;
    }

    /** 화면 표기. 템플릿에서 세 갈래 분기를 만들지 않으려고 여기에 둔다. */
    public String getLabel() {
        return label;
    }
}
