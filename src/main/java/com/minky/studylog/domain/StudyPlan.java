package com.minky.studylog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 앞으로 할 공부 한 줄. 기록({@link StudyLog})과 연관을 두지 않는다 — 계획과 실행을 잇는 안을
 * 버리고 순수 체크리스트로 간다는 결정이 있었고, 그 대가와 방어는 {@code decisions.md}「공부 계획」에.
 */
@Entity
@Table(name = "study_plan",
        indexes = @Index(name = "idx_study_plan_done", columnList = "done"))
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    /** 한 줄 덧붙임. 비면 {@code null} 이라 화면에서 줄 자체가 사라진다. */
    @Column(length = 200)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanPriority priority;

    @Column(nullable = false)
    private boolean done;

    /** {@link #done} 과 짝. 미완료면 {@code null} 이고 그 불변식은 DB 제약에도 있다. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StudyPlan() {
    }

    public StudyPlan(String title, String note, PlanPriority priority) {
        String display = collapseWhitespace(title);
        if (display.isEmpty()) {
            throw new IllegalArgumentException("할 일 제목은 필수");
        }
        this.title = display;
        this.note = emptyToNull(collapseWhitespace(note));
        this.priority = priority == null ? PlanPriority.NORMAL : priority;
        this.done = false;
    }

    /**
     * 완료 여부와 완료 시각을 <b>한 자리에서만</b> 뒤집는다. 둘은 한 사건의 두 표현이라
     * 따로 세터를 열면 "완료인데 완료 시각이 없는" 행이 만들어지고, 그 행은 예외가 아니라
     * 화면의 빈칸으로 나간다. 같은 불변식이 {@code ck_study_plan_completed_at} 에도 있다.
     */
    public void toggle() {
        this.done = !this.done;
        this.completedAt = this.done ? LocalDateTime.now() : null;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 붙여넣은 비분리 공백(U+00A0)·전각 공백(U+3000)까지 접는다. {@code (?U)} 없이는
     * {@code String.trim()} 과 {@code @NotBlank} 를 통과한 공백뿐인 제목이 그대로 저장된다.
     */
    private static String collapseWhitespace(String raw) {
        return raw == null ? "" : raw.replaceAll("(?U)\\s+", " ").trim();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getNote() {
        return note;
    }

    public PlanPriority getPriority() {
        return priority;
    }

    public boolean isDone() {
        return done;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
