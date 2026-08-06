package com.minky.studylog.web.dto;

import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 상세 화면이 쓰는 읽기 전용 표현. 지연 로딩 필드를 트랜잭션 안에서 끝내는 이유는
 * {@link StudyLogListItem} 과 같다.
 * <p>
 * 노트를 원본 대신 렌더링·새니타이즈를 마친 {@code noteHtml} 로만 들고 나간다 —
 * 템플릿이 {@code th:utext} 로 꺼낼 수 있는 노트 필드가 하나뿐이면 원본을 그대로
 * 내보내는 경로 자체가 생기지 않는다.
 */
public record StudyLogDetail(
        Long id,
        String title,
        LocalDate studyDate,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        String categoryName,
        Long categoryId,
        List<String> tags,
        String summary,
        String noteHtml) {

    public static StudyLogDetail from(StudyLog log, String noteHtml) {
        return new StudyLogDetail(
                log.getId(),
                log.getTitle(),
                log.getStudyDate(),
                log.getStartTime(),
                log.getEndTime(),
                log.getDurationMinutes(),
                log.getCategory().getName(),
                log.getCategory().getId(),
                List.copyOf(log.getTags()),
                log.getSummary(),
                noteHtml);
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
