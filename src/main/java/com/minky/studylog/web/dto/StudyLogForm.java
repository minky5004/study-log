package com.minky.studylog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;

public class StudyLogForm {

    /** 단일 사용자용 도구라 사용자 시간대를 받지 않고 고정한다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private Long id;

    @NotBlank(message = "제목은 필수")
    @Size(max = 200, message = "제목은 200자 이내")
    private String title;

    /**
     * 기본값이 오늘인 것이 "매일 쓰는 도구" 의 입력 마찰을 없애는 지점.
     * 시간대를 못박는 이유는 배포 호스트가 UTC 일 때 자정 직후 기록에 어제가 채워지기 때문.
     */
    @NotNull(message = "날짜는 필수")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate studyDate = LocalDate.now(ZONE);

    @NotNull(message = "시작 시각은 필수")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "종료 시각은 필수")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotBlank(message = "분야는 필수")
    @Size(max = 50, message = "분야는 50자 이내")
    private String categoryName;

    @Size(max = 500, message = "태그 입력이 너무 깁니다")
    private String tagsCsv;

    @Size(max = 500, message = "요약은 500자 이내")
    private String summary;

    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(LocalDate studyDate) {
        this.studyDate = studyDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTagsCsv() {
        return tagsCsv;
    }

    public void setTagsCsv(String tagsCsv) {
        this.tagsCsv = tagsCsv;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
