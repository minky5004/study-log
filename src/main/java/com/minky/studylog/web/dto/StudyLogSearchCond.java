package com.minky.studylog.web.dto;

import java.time.LocalDate;

/**
 * 목록 화면의 검색 조건. 전부 비면 조건 없는 전체 조회다.
 *
 * <p>여기서는 입력을 그대로 담기만 한다 — 빈 문자열을 {@code null} 로 접는 것과 분야·태그
 * 정규화는 서비스가 한다. 화면은 사용자가 친 값을 되비춰야 하므로 정규화된 값을 폼에 되돌리면
 * 입력창이 제멋대로 바뀐다.
 */
public class StudyLogSearchCond {

    private String keyword;
    private String categoryName;
    private String tag;
    private LocalDate from;
    private LocalDate to;

    /**
     * 조건이 하나도 걸리지 않은 상태. 화면이 "기록 없음" 과 "검색 결과 없음" 을 갈라 말하는 데
     * 쓴다 — 둘을 같은 문구로 두면 검색한 사람이 기록이 사라진 줄 안다.
     */
    public boolean isEmpty() {
        return isBlank(keyword) && isBlank(categoryName) && isBlank(tag) && from == null && to == null;
    }

    /**
     * 종료일이 시작일보다 앞선 상태. 결과가 0건인 것이 이미 확정이라 조회 자체를 건너뛰는 데
     * 쓴다 — 조용한 0건은 "조건에 맞는 기록이 없습니다" 로 나가 기록이 사라진 것으로 읽힌다.
     */
    public boolean isRangeReversed() {
        return from != null && to != null && from.isAfter(to);
    }

    private static boolean isBlank(String raw) {
        return raw == null || raw.isBlank();
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }
}
