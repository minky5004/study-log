package com.minky.studylog.web.dto;

import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 목록 화면이 쓰는 읽기 전용 표현.
 * <p>
 * 엔티티를 그대로 넘기지 않는 것은 {@code category} · {@code tags} 가 둘 다 지연 로딩이고
 * {@code spring.jpa.open-in-view=false} 이기 때문 — 템플릿이 트랜잭션 밖에서 건드리면
 * {@code LazyInitializationException} 이 난다. 변환을 서비스의 트랜잭션 안에서 끝내
 * 초기화 경계를 컴파일 시점에 고정한다.
 */
public record StudyLogListItem(
        Long id,
        String title,
        LocalDate studyDate,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        String categoryName,
        List<String> tags,
        String summary) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);

    public static StudyLogListItem from(StudyLog log) {
        return new StudyLogListItem(
                log.getId(),
                log.getTitle(),
                log.getStudyDate(),
                log.getStartTime(),
                log.getEndTime(),
                log.getDurationMinutes(),
                log.getCategory().getName(),
                List.copyOf(log.getTags()),
                log.getSummary());
    }

    /**
     * 날짜 표기를 서버에서 만든다. 템플릿에서 포맷하면 요청 로케일을 따라가,
     * 브라우저 언어가 한국어가 아닌 방문자에게 요일만 영어로 섞여 나온다.
     */
    public String studyDateText() {
        return studyDate.format(DATE_FORMAT);
    }

    /** 화면에 분 단위 총량을 그대로 내보내지 않는다 — 읽는 사람이 시간으로 환산할 일이 없게. */
    public String durationText() {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        if (hours == 0) {
            return minutes + "분";
        }
        return minutes == 0 ? hours + "시간" : hours + "시간 " + minutes + "분";
    }
}
