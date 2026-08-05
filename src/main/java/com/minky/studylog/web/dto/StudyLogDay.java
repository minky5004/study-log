package com.minky.studylog.web.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 목록의 하루치 묶음. 상자 하나가 하루다.
 * <p>
 * 묶기는 <b>한 페이지 안에서만</b> 한다. 페이지 경계에서 같은 날짜가 잘리면 양쪽 페이지에
 * 상자가 하나씩 생기고 각 합계는 그 페이지에 보이는 세션의 합이다 — 화면에 없는 세션까지
 * 더한 합계를 보여주는 쪽이 더 나쁘다.
 */
public record StudyLogDay(LocalDate date, List<StudyLogListItem> logs, int totalMinutes) {

    /** 들어온 순서를 그대로 유지한다. 정렬은 조회 쪽 책임이고 여기서 다시 손대지 않는다. */
    public static List<StudyLogDay> groupByDate(List<StudyLogListItem> logs) {
        Map<LocalDate, List<StudyLogListItem>> byDate = new LinkedHashMap<>();
        for (StudyLogListItem log : logs) {
            byDate.computeIfAbsent(log.studyDate(), date -> new ArrayList<>()).add(log);
        }
        return byDate.entrySet().stream()
                .map(entry -> new StudyLogDay(entry.getKey(), List.copyOf(entry.getValue()),
                        entry.getValue().stream().mapToInt(StudyLogListItem::durationMinutes).sum()))
                .toList();
    }

    public String dateText() {
        return DisplayText.date(date);
    }

    /** 날짜로 묶으면서 생긴 정보라 상자 머리에 둔다. */
    public String totalText() {
        return DisplayText.duration(totalMinutes);
    }

    /**
     * 세션이 하나뿐인 날은 세션 행의 소요 시간을 생략한다 — 상자 머리의 합계와 같은 값이
     * 두 줄을 먹는 것을 막기 위해서. 매일 한두 건 쓰는 도구라 드문 상황이 아니다.
     */
    public boolean singleSession() {
        return logs.size() == 1;
    }
}
