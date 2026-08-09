package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.web.dto.StudyLogDetail;
import com.minky.studylog.web.dto.StudyLogForm;
import com.minky.studylog.web.dto.StudyLogListItem;
import com.minky.studylog.web.dto.StudyLogSearchCond;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    @Autowired Validator validator;
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

        Page<StudyLogListItem> page = studyLogService.findAll(new StudyLogSearchCond(), PageRequest.of(0, 20,
                Sort.by(Sort.Direction.DESC, "studyDate", "startTime")));

        assertThat(page.getContent()).extracting(StudyLogListItem::title)
                .containsExactly("오늘 저녁", "오늘 아침", "어제 것");
        assertThat(page.getContent().get(0).categoryName()).isEqualTo("Spring");
        assertThat(page.getContent().get(0).tags()).containsExactly("인덱스", "jpa");
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

        Page<StudyLogListItem> page = studyLogService.findAll(new StudyLogSearchCond(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(StudyLogListItem::categoryColorIndex)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("검색 조건의 분야·태그는 저장 형태로 맞춰짐 — 화면 입력 표기가 흔들려도 걸림")
    void searchNormalizesCategoryAndTag() {
        studyLogService.create(form("스프링", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring Boot", "JPA"));
        entityManager.flush();
        entityManager.clear();

        // 문서에서 붙여넣은 형태 — 전각 공백(U+3000)·대소문자·앞뒤 공백이 섞인 입력
        StudyLogSearchCond cond = new StudyLogSearchCond();
        cond.setCategoryName("\u3000SPRING\u3000BOOT\u3000");
        cond.setTag("  Jpa ");

        assertThat(studyLogService.findAll(cond, PageRequest.of(0, 20)).getContent())
                .extracting(StudyLogListItem::title).containsExactly("스프링");
    }

    @Test
    @DisplayName("키워드의 LIKE 특수문자는 글자로 취급 — 와일드카드로 새면 전건이 걸린다")
    void keywordTreatsLikeMetaCharactersAsLiterals() {
        seed("진척도 100%");
        seed("a_b 표기");
        seed("axb 표기");
        entityManager.flush();
        entityManager.clear();

        assertThat(matches("100%")).isEqualTo(1);
        assertThat(matches("%")).isEqualTo(1);
        assertThat(matches("a_b")).isEqualTo(1);
        // 한 글자 와일드카드가 새면 세 건 전부 걸린다
        assertThat(matches("_")).isEqualTo(1);
    }

    @Test
    @DisplayName("검색어 대소문자는 서비스가 접음 — 쿼리가 파라미터를 lower() 로 감싸지 않으므로")
    void keywordIsLoweredBeforeReachingQuery() {
        seed("JPA 기초");
        entityManager.flush();
        entityManager.clear();

        assertThat(matches("JPA")).isEqualTo(1);
        assertThat(matches("jpa")).isEqualTo(1);
    }

    private void seed(String title) {
        studyLogService.create(form(title, LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", "jpa"));
    }

    private long matches(String keyword) {
        StudyLogSearchCond cond = new StudyLogSearchCond();
        cond.setKeyword(keyword);
        return studyLogService.findAll(cond, PageRequest.of(0, 20)).getTotalElements();
    }

    @Test
    @DisplayName("키워드도 유니코드 공백을 접음 — 붙여넣은 비분리 공백에 무결과가 되지 않게")
    void searchCollapsesUnicodeWhitespaceInKeyword() {
        studyLogService.create(form("트랜잭션 격리 수준", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", "jpa"));
        entityManager.flush();
        entityManager.clear();

        // 비분리 공백(U+00A0)은 String.isBlank()·trim() 이 보지 못한다 — 분야·태그는 이미 접힌다
        StudyLogSearchCond cond = new StudyLogSearchCond();
        cond.setKeyword("\u00A0격리\u00A0수준\u00A0");

        assertThat(studyLogService.findAll(cond, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("공백뿐인 조건은 조건 없음 — 아무것도 걸리지 않는 검색이 되지 않게")
    void blankConditionsAreTreatedAsAbsent() {
        studyLogService.create(form("스프링", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Spring", "jpa"));
        entityManager.flush();
        entityManager.clear();

        StudyLogSearchCond cond = new StudyLogSearchCond();
        cond.setKeyword("   ");
        cond.setCategoryName("\u3000");
        cond.setTag(" ");

        assertThat(studyLogService.findAll(cond, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DB 왕복 후에도 태그 구성·순서 그대로")
    void keepsTagsAcrossReload() {
        studyLogService.create(form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(14, 0), LocalTime.of(15, 20), "CS", "자료   구조, 큐"));

        entityManager.flush();
        entityManager.clear();

        Page<StudyLogListItem> page = studyLogService.findAll(new StudyLogSearchCond(), PageRequest.of(0, 20));

        assertThat(page.getContent().get(0).tags()).containsExactly("자료 구조", "큐");
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
        assertThat(detail.tags()).containsExactly("자료 구조", "큐");
        assertThat(detail.durationText()).isEqualTo("1시간 20분");
        assertThat(detail.noteHtml()).contains("<h1>큐</h1>").doesNotContain("script");
    }

    @Test
    @DisplayName("없는 id 는 전용 예외 — 다른 조회 실패가 404 로 둔갑하지 않게 타입을 좁힌다")
    void throwsForMissingId() {
        assertThatThrownBy(() -> studyLogService.findById(9_999L))
                .isInstanceOf(StudyLogNotFoundException.class);
    }

    @Test
    @DisplayName("수정은 소요 시간 재계산 · 태그 교체")
    void updateRecalculatesDurationAndTags() {
        Long id = studyLogService.create(form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", "jpa"));

        entityManager.flush();
        entityManager.clear();

        studyLogService.update(id, form("트랜잭션 격리 수준", LocalDate.of(2026, 8, 6),
                LocalTime.of(23, 0), LocalTime.of(1, 30), "Spring", "트랜잭션, JPA"));

        entityManager.flush();
        entityManager.clear();

        StudyLog updated = studyLogRepository.findById(id).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("트랜잭션 격리 수준");
        assertThat(updated.getDurationMinutes()).isEqualTo(150);
        assertThat(updated.getTags()).containsExactly("트랜잭션", "jpa");
        assertThat(updated.getCategory().getName()).isEqualTo("Spring");
    }

    /**
     * 자리만 맞바꾸는 수정은 하이버네이트가 행을 지웠다 넣지 않고 자리마다 값을 갈아 끼우는
     * 경로다 — 태그를 더하거나 빼는 수정만으로는 지나가지 않는다. {@code StudyLog.tags} 가
     * {@code (study_log_id, tag)} 유일 제약을 두지 않는 이유가 여기 있고, 제약을 되붙이면
     * 이 테스트가 그 자리에서 죽는다.
     */
    @Test
    @DisplayName("태그를 자리만 맞바꾸는 수정도 순서 반영")
    void updateSwapsTagPositions() {
        Long id = studyLogService.create(form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", "자료 구조, 큐"));

        entityManager.flush();
        entityManager.clear();

        studyLogService.update(id, form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", "큐, 자료 구조"));

        entityManager.flush();
        entityManager.clear();

        assertThat(studyLogRepository.findById(id).orElseThrow().getTags())
                .containsExactly("큐", "자료 구조");
    }

    /**
     * 스칼라 필드가 그대로면 엔티티 행이 더럽지 않아 {@code @PreUpdate} 가 불리지 않는다 —
     * 태그만 고친 기록의 수정 시각이 옛 값으로 남는 경로.
     */
    @Test
    @DisplayName("태그만 바꿔도 수정 시각 갱신")
    void updateStampsUpdatedAtWhenOnlyTagsChange() {
        Long id = studyLogService.create(form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", "jpa"));

        entityManager.flush();
        entityManager.clear();

        LocalDateTime beforeUpdate = LocalDateTime.now();
        studyLogService.update(id, form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", "jpa, 큐"));

        entityManager.flush();
        entityManager.clear();

        StudyLog updated = studyLogRepository.findById(id).orElseThrow();
        assertThat(updated.getTags()).containsExactly("jpa", "큐");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("없는 기록 수정은 전용 예외")
    void updateThrowsForMissingId() {
        StudyLogForm form = form("없는 기록", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", null);

        assertThatThrownBy(() -> studyLogService.update(9_999L, form))
                .isInstanceOf(StudyLogNotFoundException.class);
    }

    @Test
    @DisplayName("삭제하면 태그까지 함께 사라짐")
    void deleteRemovesLogAndTags() {
        Long id = studyLogService.create(form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", "jpa, 큐"));

        entityManager.flush();
        entityManager.clear();

        studyLogService.delete(id);

        entityManager.flush();
        entityManager.clear();

        assertThat(studyLogRepository.findById(id)).isEmpty();
        Number tagRows = (Number) entityManager
                .createNativeQuery("select count(*) from study_log_tag").getSingleResult();
        assertThat(tagRows.longValue()).isZero();
    }

    @Test
    @DisplayName("없는 기록 삭제는 전용 예외")
    void deleteThrowsForMissingId() {
        assertThatThrownBy(() -> studyLogService.delete(9_999L))
                .isInstanceOf(StudyLogNotFoundException.class);
    }

    @Test
    @DisplayName("수정 폼 프리필은 노트를 원본 마크다운으로 — 렌더링된 HTML 을 다시 편집시키지 않는다")
    void toFormPrefillsRawNote() {
        StudyLogForm created = form("자료 구조 복습", LocalDate.of(2026, 8, 6),
                LocalTime.of(14, 0), LocalTime.of(15, 20), "CS", "자료 구조, 큐");
        created.setNote("# 큐\n\n- 선입선출");
        Long id = studyLogService.create(created);

        entityManager.flush();
        entityManager.clear();

        StudyLogForm prefilled = studyLogService.toForm(id);

        assertThat(prefilled.getId()).isEqualTo(id);
        assertThat(prefilled.getTitle()).isEqualTo("자료 구조 복습");
        assertThat(prefilled.getStudyDate()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(prefilled.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(prefilled.getCategoryName()).isEqualTo("CS");
        assertThat(prefilled.getNote()).isEqualTo("# 큐\n\n- 선입선출");
        assertThat(prefilled.getTagsCsv().split(",\\s*"))
                .containsExactly("자료 구조", "큐");
    }

    /**
     * 프리필이 입력보다 길어지면 손대지 않은 태그만으로 폼이 반려돼, 저장에 성공한 기록을
     * 제목 한 줄도 못 고치게 된다.
     */
    @Test
    @DisplayName("생성이 받아준 태그 입력은 수정 폼으로 되돌아와도 다시 제출 가능")
    void toFormKeepsTagsCsvSubmittable() {
        // 20개 · 각 24자 — 쉼표까지 499자로 태그 개수·길이·입력 길이 상한에 모두 걸리지 않는 최대치
        String tagsCsv = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "%02d".formatted(i) + "a".repeat(22))
                .collect(Collectors.joining(","));
        Long id = studyLogService.create(form("태그 가득한 기록", LocalDate.of(2026, 8, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "CS", tagsCsv));

        entityManager.flush();
        entityManager.clear();

        assertThat(validator.validate(studyLogService.toForm(id))).isEmpty();
    }

    @Test
    @DisplayName("없는 기록의 수정 폼은 전용 예외")
    void toFormThrowsForMissingId() {
        assertThatThrownBy(() -> studyLogService.toForm(9_999L))
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
