package com.minky.studylog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "study_log", indexes = {
        @Index(name = "idx_study_log_study_date", columnList = "study_date"),
        @Index(name = "idx_study_log_category_id", columnList = "category_id")
})
public class StudyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_log_tag",
            joinColumns = @JoinColumn(name = "study_log_id"),
            indexes = @Index(name = "idx_study_log_tag_tag", columnList = "tag"))
    @Column(name = "tag", length = 50, nullable = false)
    @BatchSize(size = 50)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StudyLog() {
    }

    public StudyLog(String title, LocalDate studyDate, LocalTime startTime, LocalTime endTime,
                    Category category, Set<String> tags, String summary, String note) {
        apply(title, studyDate, startTime, endTime, category, tags, summary, note);
    }

    public void update(String title, LocalDate studyDate, LocalTime startTime, LocalTime endTime,
                       Category category, Set<String> tags, String summary, String note) {
        apply(title, studyDate, startTime, endTime, category, tags, summary, note);
    }

    private void apply(String title, LocalDate studyDate, LocalTime startTime, LocalTime endTime,
                       Category category, Set<String> tags, String summary, String note) {
        this.title = title;
        this.studyDate = studyDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = StudySessionTime.durationMinutes(startTime, endTime);
        this.category = category;
        this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);
        this.summary = summary;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStudyDate() {
        return studyDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Category getCategory() {
        return category;
    }

    /**
     * 입력 순서를 유지한 채 복사해 돌려준다. {@code Set.copyOf} 는 순회 순서를 보장하지
     * 않아 화면마다 태그 순서가 흔들리고, 원소가 null 이면 조회 자체가 NPE 로 죽는다.
     */
    public Set<String> getTags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public String getSummary() {
        return summary;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
