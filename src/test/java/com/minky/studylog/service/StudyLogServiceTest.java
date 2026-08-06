package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.web.dto.StudyLogDetail;
import com.minky.studylog.web.dto.StudyLogForm;
import com.minky.studylog.web.dto.StudyLogListItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudyLogServiceTest {

    @Autowired StudyLogService studyLogService;
    @Autowired StudyLogRepository studyLogRepository;
    @PersistenceContext EntityManager entityManager;

    @Test
    @DisplayName("폼 입력이 정규화를 거쳐 저장 · 소요 시간 자동 계산")
    void createsWithNormalizedTagsAndDuration() {
        Long id = studyLogService.create(form("트랜잭션 격리 수준", LocalDate.of(2026, 8, 3),
                LocalTime.of(23, 0), LocalTime.of(1, 0), "spring", "JPA, 트랜잭션, jpa"));

        StudyLog saved = studyLogRepository.findById(id).orElseThrow();
        assertThat(saved.getDurationMinutes()).isEqualTo(120);
        assertThat(saved.getTags()).containsExactly("jpa", "트랜잭션");
        assertThat(saved.getCategory().getName()).isEqualTo("spring");
    }

    @Test
    @DisplayName("대소문자만 다른 분야는 같은 행 재사용 — 통계가 갈라지지 않게")
    void reusesCategoryIgnoringCase() {
        Long first = studyLogService.create(form("첫 기록", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", null));
        Long second = studyLogService.create(form("둘째 기록", LocalDate.of(2026, 8, 4),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "spring", null));

        assertThat(studyLogRepository.findById(first).orElseThrow().getCategory().getId())
                .isEqualTo(studyLogRepository.findById(second).orElseThrow().getCategory().getId());
    }

    @Test
    @DisplayName("시작·종료가 같으면 저장 거부 — 0분과 24시간을 구별할 수 없음")
    void rejectsSameStartAndEnd() {
        StudyLogForm form = form("같은 시각", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(9, 0), "Spring", null);

        assertThatThrownBy(() -> studyLogService.create(form))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("목록은 날짜 · 시작 시각 역순 · 지연 로딩 필드가 채워진 DTO")
    void findsAllInReverseChronologicalOrder() {
        studyLogService.create(form("어제 것", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", "jpa"));
        studyLogService.create(form("오늘 아침", LocalDate.of(2026, 8, 4),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", "jpa"));
        studyLogService.create(form("오늘 저녁", LocalDate.of(2026, 8, 4),
                LocalTime.of(23, 0), LocalTime.of(1, 0), "Spring", "인덱스, jpa"));

        // 1차 캐시에서 같은 인스턴스를 돌려받으면 매핑 결함이 가려진다 — DB 를 실제로 왕복시킨다
        entityManager.flush();
        entityManager.clear();

        Page<StudyLogListItem> page = studyLogService.findAll(PageRequest.of(0, 20,
                Sort.by(Sort.Direction.DESC, "studyDate", "startTime")));

        assertThat(page.getContent()).extracting(StudyLogListItem::title)
                .containsExactly("오늘 저녁", "오늘 아침", "어제 것");
        assertThat(page.getContent().get(0).categoryName()).isEqualTo("Spring");
        assertThat(page.getContent().get(0).tags()).containsExactlyInAnyOrder("인덱스", "jpa");
        assertThat(page.getContent().get(0).durationText()).isEqualTo("2시간");
    }

    @Test
    @DisplayName("서로 다른 분야는 서로 다른 색 — 목록이 색으로 분야를 가른다")
    void assignsDistinctColorsToDifferentCategories() {
        studyLogService.create(form("스프링", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", null));
        studyLogService.create(form("자료 구조", LocalDate.of(2026, 8, 4),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", null));

        entityManager.flush();
        entityManager.clear();

        Page<StudyLogListItem> page = studyLogService.findAll(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(StudyLogListItem::categoryColorIndex)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("DB 왕복 후에도 태그 구성은 그대로 — 순서는 보장 대상 아님")
    void keepsTagsAcrossReload() {
        studyLogService.create(form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(14, 0), LocalTime.of(15, 20), "CS", "자료   구조, 큐"));

        entityManager.flush();
        entityManager.clear();

        Page<StudyLogListItem> page = studyLogService.findAll(PageRequest.of(0, 20));

        // 순서까지 단언하지 않는 것은 @ElementCollection Set 이 해시 순서로 실려 오기 때문 —
        // 입력 순서 보존은 컬렉션 타입·스키마를 바꿔야 해서 도메인 사이클에서 따로 다룬다
        assertThat(page.getContent().get(0).tags()).containsExactlyInAnyOrder("자료 구조", "큐");
    }

    @Test
    @DisplayName("상세는 지연 로딩 필드가 채워진 DTO · 노트는 새니타이즈를 끝낸 HTML")
    void findsDetailWithRenderedNote() {
        StudyLogForm form = form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(14, 0), LocalTime.of(15, 20), "CS", "자료 구조, 큐");
        form.setNote("# 큐\n\n<script>alert(1)</script>");
        Long id = studyLogService.create(form);

        entityManager.flush();
        entityManager.clear();

        StudyLogDetail detail = studyLogService.findById(id);

        assertThat(detail.title()).isEqualTo("자료 구조 복습");
        assertThat(detail.categoryName()).isEqualTo("CS");
        assertThat(detail.tags()).containsExactlyInAnyOrder("자료 구조", "큐");
        assertThat(detail.durationText()).isEqualTo("1시간 20분");
        assertThat(detail.noteHtml()).contains("<h1>큐</h1>").doesNotContain("script");
    }

    @Test
    @DisplayName("없는 id 는 전용 예외 — 다른 조회 실패가 404 로 둔갑하지 않게 타입을 좁힌다")
    void throwsForMissingId() {
        assertThatThrownBy(() -> studyLogService.findById(9_999L))
                .isInstanceOf(StudyLogNotFoundException.class);
    }

    private StudyLogForm form(String title, LocalDate studyDate, LocalTime start, LocalTime end,
                              String categoryName, String tagsCsv) {
        StudyLogForm form = new StudyLogForm();
        form.setTitle(title);
        form.setStudyDate(studyDate);
        form.setStartTime(start);
        form.setEndTime(end);
        form.setCategoryName(categoryName);
        form.setTagsCsv(tagsCsv);
        form.setSummary("요약");
        form.setNote("# 노트");
        return form;
    }
}
