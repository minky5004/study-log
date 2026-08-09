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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
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

    /**
     * 순서를 컬럼으로 남긴다. {@code Set} 으로 매핑하면 실려 올 때 {@code HashSet} 기반
     * {@code PersistentSet} 이라 순회 순서가 해시 순서 — 입력 {@code 자료 구조, 큐} 가 화면에서
     * {@code 큐, 자료 구조} 로 뒤집힌다. 필드에 {@code LinkedHashSet} 을 두어도 그것은 첫 저장
     * 전까지만 유효해, 순서를 되살릴 근거가 DB 에 없으면 왕복 뒤에는 복구할 방법이 없다.
     * <p>
     * 그래서 필드는 {@code List} 지만 경계 타입은 {@link SequencedSet} 이다 — 저장 형태는
     * 순서 있는 행이고 도메인 규칙은 "순서 있는 중복 없음" 이라, 둘을 같은 타입으로 맞추면
     * 어느 한쪽이 거짓말이 된다.
     * <p>
     * <b>{@code (study_log_id, tag)} 유일 제약은 두지 않는다.</b> {@code Set} 매핑일 때는 그
     * 조합이 기본 키였지만 순서 컬럼이 그 자리를 가져갔고, 같은 조합을 제약으로 되붙이면
     * 태그 자리를 맞바꾸는 수정이 죽는다 — 하이버네이트가 자리마다
     * {@code update study_log_tag set tag=? where study_log_id=? and tag_order=?} 를 내는데,
     * 즉시 검사되는 제약에서는 플러시 중간에 같은 태그가 두 행에 겹치는 순간이 생긴다.
     * 지연 검사는 PostgreSQL 에만 있어 H2 와 갈라지므로 쓰지 않는다. 중복 방어는 경계 타입이
     * {@code Set} 인 것으로 남는다 — 들어오는 길이 전부 그 타입이라 중복은 만들 수가 없다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "study_log_tag",
            joinColumns = @JoinColumn(name = "study_log_id"),
            indexes = @Index(name = "idx_study_log_tag_tag", columnList = "tag"))
    @Column(name = "tag", length = 50, nullable = false)
    @OrderColumn(name = "tag_order")
    @BatchSize(size = 50)
    private List<String> tags = new ArrayList<>();

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
                    Category category, SequencedSet<String> tags, String summary, String note) {
        applyScalars(title, studyDate, startTime, endTime, category, summary, note);
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    /**
     * 태그는 컬렉션 인스턴스를 갈아 끼우지 않고 제자리로 갱신한다 — 새 컬렉션을 대입하면
     * 로드된 {@code PersistentList} 가 버려져 태그가 그대로여도 {@code study_log_tag} 전 행이
     * 지워졌다 다시 들어간다.
     * <p>
     * 수정 시각도 직접 찍는다. {@code @PreUpdate} 는 엔티티 행에 {@code UPDATE} 가 잡힐 때만
     * 불리는데, 태그만 고치면 스칼라 필드는 같은 값이 재대입될 뿐이라 행이 더럽지 않다 —
     * 컬렉션 행은 다시 쓰이는데 수정 시각만 옛 값으로 남는다.
     */
    public void update(String title, LocalDate studyDate, LocalTime startTime, LocalTime endTime,
                       Category category, SequencedSet<String> tags, String summary, String note) {
        applyScalars(title, studyDate, startTime, endTime, category, summary, note);
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        this.updatedAt = LocalDateTime.now();
    }

    private void applyScalars(String title, LocalDate studyDate, LocalTime startTime,
                              LocalTime endTime, Category category, String summary, String note) {
        this.title = title;
        this.studyDate = studyDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = StudySessionTime.durationMinutes(startTime, endTime);
        this.category = category;
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
    public SequencedSet<String> getTags() {
        return Collections.unmodifiableSequencedSet(new LinkedHashSet<>(tags));
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
