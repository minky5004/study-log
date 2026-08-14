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

    HIGH("높음", "prio-high"),
    NORMAL("보통", "prio-normal"),
    LOW("낮음", "prio-low");

    private final String label;
    private final String styleClass;

    PlanPriority(String label, String styleClass) {
        this.label = label;
        this.styleClass = styleClass;
    }

    /** 화면 표기. 템플릿에서 세 갈래 분기를 만들지 않으려고 여기에 둔다. */
    public String getLabel() {
        return label;
    }

    /**
     * 우선순위 띠의 CSS 클래스. 상수 이름에서 파생하지 않고 적어 두는 것은 대소문자 변환이
     * 로캘을 타기 때문 — 터키어 로캘에서 {@code "HIGH".toLowerCase()} 는 {@code hıgh} 라
     * 규칙이 어긋나고, 그 실패는 예외가 아니라 <b>띠 색이 사라지는 것</b>으로만 드러난다.
     */
    public String getStyleClass() {
        return styleClass;
    }
}
