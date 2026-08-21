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

    /**
     * 규칙이 아니라 방어선이다. 태그의 규칙은 개수 20과 하나당 50자이고 csv 총길이는 그 파생값이라,
     * 규칙 자리의 상한은 컨트롤러가 정규화한 결과로 잰다. 여기 남긴 4,000자는 규칙을 갑절로
     * 늘려도 걸리지 않는 값이면서, 붙여넣은 문서 하나가 정규화의 split·치환을 먼저 지나
     * 512MB 인스턴스의 메모리를 먹는 것을 막는다.
     */
    @Size(max = 4000, message = "태그 입력이 너무 깁니다")
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
