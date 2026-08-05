package com.minky.studylog.web.dto;

import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
        Long categoryId,
        List<String> tags,
        String summary) {

    public static StudyLogListItem from(StudyLog log) {
        return new StudyLogListItem(
                log.getId(),
                log.getTitle(),
                log.getStudyDate(),
                log.getStartTime(),
                log.getEndTime(),
                log.getDurationMinutes(),
                log.getCategory().getName(),
                log.getCategory().getId(),
                List.copyOf(log.getTags()),
                log.getSummary());
    }

    public String studyDateText() {
        return DisplayText.date(studyDate);
    }

    public String durationText() {
        return DisplayText.duration(durationMinutes);
    }

    public int categoryColorIndex() {
        return CategoryPalette.indexOf(categoryId);
    }
}
