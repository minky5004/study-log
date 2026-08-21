package com.minky.studylog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.projection.CategoryTotal;
import com.minky.studylog.repository.projection.DailyTotal;
import com.minky.studylog.repository.projection.StartTimeSlice;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 리포지토리는 <b>실제 PostgreSQL 위에서</b> 돈다. 근거는 {@link PostgresTestContainer}.
 *
 * <p>{@code Replace.NONE} 이 필요한 것은 {@code @DataJpaTest} 가 데이터소스를 내장 DB 로
 * 갈아 끼우는 것을 기본으로 하기 때문 — 빼면 컨테이너는 뜨지만 아무도 접속하지 않는다.
 */
@DataJpaTest(properties = {
        // 여기서만 운영과 같은 배선으로 돌린다 — 스키마는 Flyway 가 만들고 하이버네이트는 대조만.
        // 엔티티에 필드를 더하고 마이그레이션을 빠뜨리면 이 클래스 전체가 컨텍스트 기동에서 멈춘다.
        // `create-drop` 으로 두면 하이버네이트가 스키마를 대신 만들어 그 누락이 배포까지 살아남는다
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainer.class)
@ActiveProfiles("test")
class StudyLogRepositoryTest {

    /** 컨트롤러가 목록에 쓰는 크기·정렬 — 계측이 화면과 다른 조건을 재면 회귀를 놓친다. */
    private static final int PAGE_SIZE = 20;
    private static final Sort LIST_SORT =
            Sort.by(Sort.Direction.DESC, "studyDate", "startTime", "id");
    private static final Pageable PAGE = PageRequest.of(0, PAGE_SIZE, LIST_SORT);

    @Autowired StudyLogRepository studyLogRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TestEntityManager entityManager;
    @Autowired DataSource dataSource;

    /**
     * 이 파일의 나머지 전부가 기대는 전제라 먼저 세운다. {@code test} 프로파일에는 H2 접속정보가
     * 그대로 남아 있고 {@code Replace.NONE} 이 내장 DB 대체까지 껐으므로, {@code @Import} 가
     * 빠지거나 부트 업그레이드로 {@code @ServiceConnection} 배선이 어긋나면 열일곱 개가 조용히
     * H2 로 되돌아가 <b>초록인 채</b> 남는다 — 이 클래스를 옮긴 이유가 바로 그 초록이었다.
     */
    @Test
    @DisplayName("실제 PostgreSQL 위에서 도는지부터 확인 — H2 로 되돌아가도 초록인 자리")
    void runsOnRealPostgres() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:postgresql:");
        }
    }

    @Test
    @DisplayName("자정 넘김 세션이 저장 시점 계산값으로 저장")
    void savesDerivedDuration() {
        Category spring = categoryRepository.save(new Category("Spring"));
        Long id = studyLogRepository.save(new StudyLog(
                "트랜잭션 격리 수준", LocalDate.of(2026, 8, 3),
                LocalTime.of(23, 0), LocalTime.of(1, 0),
                spring, new LinkedHashSet<>(List.of("jpa", "트랜잭션")),
                "격리 수준 4단계 정리", "# 노트")).getId();

        // 필드 값이 아니라 컬럼에 실제로 들어갔는지 봐야 매핑 결함이 드러난다
        entityManager.flush();
        entityManager.clear();

        StudyLog found = studyLogRepository.findById(id).orElseThrow();
        assertThat(found.getDurationMinutes()).isEqualTo(120);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getTags()).containsExactly("jpa", "트랜잭션");
        assertThat(found.getCategory().getName()).isEqualTo("Spring");
        assertThat(found.getNote()).isEqualTo("# 노트");
    }

    /**
     * 도메인 단위 테스트({@code StudyLogTest})는 같은 순서를 이미 단언하지만 DB 를 거치지 않아
     * 통과했다. 순서가 사라지는 곳이 왕복이라 그물도 왕복에 있어야 한다 — 실려 올 때 순서를
     * 되살릴 근거가 컬럼으로 남아 있지 않으면 여기서만 무너진다.
     */
    @Test
    @DisplayName("태그 입력 순서가 DB 왕복 뒤에도 유지")
    void keepsTagOrderAcrossRoundTrip() {
        Category cs = categoryRepository.save(new Category("CS"));
        Long id = studyLogRepository.save(new StudyLog(
                "자료 구조 훑기", LocalDate.of(2026, 8, 3),
                LocalTime.of(20, 0), LocalTime.of(21, 0),
                cs, new LinkedHashSet<>(List.of("자료 구조", "큐")),
                "요약", "# 노트")).getId();
        flushAndClear();

        assertThat(studyLogRepository.findById(id).orElseThrow().getTags())
                .containsExactly("자료 구조", "큐");
    }

    @Test
    @DisplayName("분야는 정규화 키로 조회 — 최초 등록 표기와 무관")
    void findsCategoryByNormalizedKey() {
        categoryRepository.save(new Category("Spring"));
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findByNameKey("spring")).isPresent();
    }

    @Test
    @DisplayName("대소문자만 다른 분야는 DB 가 거부 — 분야 해석이 갈라지는 것의 최종 방어선")
    void rejectsCaseOnlyDuplicateCategory() {
        categoryRepository.saveAndFlush(new Category("Spring"));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new Category("spring")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("목록 한 페이지 조회에 분야 쿼리가 행마다 나가지 않음")
    void listPageDoesNotTriggerNPlusOne() {
        // 분야를 행마다 다르게 둔다 — 한 분야를 공유하면 영속성 컨텍스트가 첫 조회 뒤 캐시해
        // N+1 이 1회로 접히고, fetch join 을 되돌려도 테스트가 통과해 버린다
        for (int i = 0; i < PAGE_SIZE; i++) {
            Category category = categoryRepository.save(new Category("분야 " + i));
            studyLogRepository.save(sample("기록 " + i, category));
        }
        entityManager.flush();
        entityManager.clear();

        Statistics stats = statistics();
        // 계측이 꺼지면 모든 카운터가 0 이라 상한 단언이 무엇도 재지 않고 통과한다
        assertThat(stats.isStatisticsEnabled()).isTrue();
        stats.clear();

        Page<StudyLog> page = studyLogRepository.search(null, null, null, null, null, PAGE);
        // 화면에 나가는 접근을 그대로 재현한다 — 목록 DTO 가 분야명과 태그를 모두 읽는다
        page.getContent().forEach(log -> {
            log.getCategory().getName();
            log.getTags().size();
        });

        // 빈 페이지는 지연 로딩을 한 번도 건드리지 않고 상한을 통과한다 — 잰 대상부터 고정한다
        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        // 목록 1 + count 1 + 태그 배치 1
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("키워드는 제목·요약·노트 본문을 함께 훑음")
    void searchesTitleSummaryAndNote() {
        saveLog("JPA 기초", "요약", "본문 없음");
        saveLog("무관한 제목", "격리 수준 요약", "본문 없음");
        saveLog("무관한 제목2", "요약", "# 트랜잭션 격리 수준\n본문");
        flushAndClear();

        assertThat(search("격리").getTotalElements()).isEqualTo(2);
        assertThat(search("JPA").getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드 대소문자 무시")
    void searchIsCaseInsensitive() {
        saveLog("JPA 기초", "요약", null);
        flushAndClear();

        assertThat(search("jpa").getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("노트·요약이 비어도 제목 일치 기록이 누락되지 않음")
    void nullNoteDoesNotBreakSearch() {
        saveLog("JPA 기초", null, null);
        flushAndClear();

        assertThat(search("기초").getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("분야 · 태그 · 기간 조합으로 좁혀짐")
    void combinesFilters() {
        LocalDate inRange = LocalDate.of(2026, 8, 10);
        saveLog("대상", "요약", null, "Spring", List.of("jpa"), inRange);
        saveLog("분야 다름", "요약", null, "CS", List.of("jpa"), inRange);
        saveLog("태그 다름", "요약", null, "Spring", List.of("http"), inRange);
        saveLog("기간 밖", "요약", null, "Spring", List.of("jpa"), LocalDate.of(2026, 7, 31));
        flushAndClear();

        Page<StudyLog> found = studyLogRepository.search(null, Category.toKey("spring"), "jpa",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), PAGE);

        assertThat(found.getContent()).extracting(StudyLog::getTitle).containsExactly("대상");
    }

    /**
     * 한쪽만 준 범위가 실제로 열린 구간으로 도는지 본다 — 위 조합 테스트는 두 경계를 다 채워
     * 이 의미를 보지 않는다. 화면의 기간 칸 둘은 서로 없어도 되므로 이쪽이 더 흔한 형태다.
     *
     * <p>캐스트를 지키는 것은 이 테스트가 아니다. PostgreSQL 은 값이 아니라 SQL 문맥으로
     * 파라미터 타입을 정하므로, 캐스트가 빠지면 경계를 채운 검색도 함께 죽는다.
     */
    @Test
    @DisplayName("시작·종료 한쪽만 준 기간 검색")
    void filtersByOpenEndedPeriod() {
        saveLog("이른 날", "요약", null, "Spring", List.of("jpa"), LocalDate.of(2026, 7, 31));
        saveLog("늦은 날", "요약", null, "Spring", List.of("jpa"), LocalDate.of(2026, 8, 10));
        flushAndClear();

        LocalDate august = LocalDate.of(2026, 8, 1);
        assertThat(studyLogRepository.search(null, null, null, august, null, PAGE).getContent())
                .extracting(StudyLog::getTitle).containsExactly("늦은 날");
        assertThat(studyLogRepository.search(null, null, null, null, august, PAGE).getContent())
                .extracting(StudyLog::getTitle).containsExactly("이른 날");
    }

    @Test
    @DisplayName("조건이 전부 비면 전체 조회")
    void emptyConditionReturnsAll() {
        saveLog("하나", "요약", null);
        saveLog("둘", "요약", null);
        flushAndClear();

        assertThat(studyLogRepository.search(null, null, null, null, null, PAGE).getTotalElements())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("태그 제안은 사용 빈도 내림차순 · 동률은 이름순")
    void suggestsTagsByUsage() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        saveLog("하나", "요약", null, "Spring", List.of("jpa", "큐"), date);
        saveLog("둘", "요약", null, "Spring", List.of("jpa"), date);
        saveLog("셋", "요약", null, "Spring", List.of("jpa", "http"), date);
        flushAndClear();

        // 동률까지 정하지 않으면 순서가 DB 마다 갈려 화면 제안이 흔들린다
        assertThat(studyLogRepository.findDistinctTagsByUsage())
                .containsExactly("jpa", "http", "큐");
    }

    @Test
    @DisplayName("분야 제안은 최초 등록 표기 그대로 · 대소문자 무시 이름순")
    void suggestsCategoryNamesIgnoringCase() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        saveLog("하나", "요약", null, "Spring", List.of("jpa"), date);
        saveLog("둘", "요약", null, "algorithm", List.of("jpa"), date);
        saveLog("셋", "요약", null, "CS", List.of("jpa"), date);
        flushAndClear();

        // name 으로 정렬하면 대문자가 앞으로 몰려 CS · Spring · algorithm 이 된다
        assertThat(categoryRepository.findUsedNamesOrderedByKey())
                .containsExactly("algorithm", "CS", "Spring");
    }

    @Test
    @DisplayName("기록이 하나도 없는 분야는 제안에서 빠진다")
    void skipsCategoriesWithoutLogs() {
        saveLog("하나", "요약", null, "Spring", List.of("jpa"), LocalDate.of(2026, 8, 3));
        // 분야를 지우는 경로가 없어 오타로 만든 행은 영구히 남는다 — 제안까지 남기면 재생산된다
        categoryRepository.save(new Category("Spirng"));
        flushAndClear();

        assertThat(categoryRepository.findUsedNamesOrderedByKey())
                .containsExactly("Spring");
    }

    @Test
    @DisplayName("일별 합계는 같은 날 세션을 접어 기간 안만 · 날짜 오름차순")
    void sumsMinutesPerDay() {
        saveLog("아침", "요약", null, "Spring", List.of("jpa"), LocalDate.of(2026, 8, 3));
        saveLog("저녁", "요약", null, "CS", List.of("jpa"), LocalDate.of(2026, 8, 3));
        saveLog("다음 날", "요약", null, "CS", List.of("jpa"), LocalDate.of(2026, 8, 4));
        saveLog("기간 밖", "요약", null, "CS", List.of("jpa"), LocalDate.of(2026, 8, 10));
        flushAndClear();

        assertThat(studyLogRepository.findDailyTotals(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5)))
                .containsExactly(
                        new DailyTotal(LocalDate.of(2026, 8, 3), 120),
                        new DailyTotal(LocalDate.of(2026, 8, 4), 60));
    }

    @Test
    @DisplayName("분야별 합계는 내림차순 · 색 배정에 쓸 식별자 동반")
    void sumsMinutesPerCategory() {
        saveLog("하나", "요약", null, "Spring", List.of("jpa"), LocalDate.of(2026, 8, 3));
        saveLog("둘", "요약", null, "CS", List.of("jpa"), LocalDate.of(2026, 8, 3));
        saveLog("셋", "요약", null, "CS", List.of("jpa"), LocalDate.of(2026, 8, 4));
        flushAndClear();

        assertThat(studyLogRepository.findCategoryTotals())
                .extracting(CategoryTotal::categoryName, CategoryTotal::totalMinutes)
                .containsExactly(tuple("CS", 120L), tuple("Spring", 60L));
        assertThat(studyLogRepository.findCategoryTotals().getFirst().categoryId()).isNotNull();
    }

    @Test
    @DisplayName("시간대 재료는 시작 시각과 분만 — 자정 넘김도 계산 없이 그대로")
    void readsStartTimeSlices() {
        Category spring = categoryRepository.save(new Category("Spring"));
        studyLogRepository.save(new StudyLog("자정 넘김", LocalDate.of(2026, 8, 3),
                LocalTime.of(23, 0), LocalTime.of(1, 0), spring,
                new LinkedHashSet<>(List.of("jpa")), "요약", null));
        flushAndClear();

        assertThat(studyLogRepository.findStartTimeSlices())
                .containsExactly(new StartTimeSlice(LocalTime.of(23, 0), 120));
    }

    @Test
    @DisplayName("같은 시각 시작 세션은 DB 에서 합쳐져 옴 — 행 수가 기록 수만큼 올라오지 않게")
    void foldsSlicesBySameStartTime() {
        Category spring = categoryRepository.save(new Category("Spring"));
        for (int i = 0; i < 3; i++) {
            studyLogRepository.save(new StudyLog("세션 " + i, LocalDate.of(2026, 8, 3).plusDays(i),
                    LocalTime.of(20, 0), LocalTime.of(21, 0), spring,
                    new LinkedHashSet<>(List.of("jpa")), "요약", null));
        }
        flushAndClear();

        assertThat(studyLogRepository.findStartTimeSlices())
                .containsExactly(new StartTimeSlice(LocalTime.of(20, 0), 180));
    }

    /**
     * 레포지토리는 감싸기·이스케이프·소문자화가 끝난 패턴을 받는다 — 그 규칙 자체는 서비스가 소유.
     * 여기서 소문자로 내리는 것이 그 계약의 재현이다.
     */
    private Page<StudyLog> search(String keyword) {
        return studyLogRepository.search(
                ("%" + keyword + "%").toLowerCase(Locale.ROOT), null, null, null, null, PAGE);
    }

    private void saveLog(String title, String summary, String note) {
        saveLog(title, summary, note, "Spring", List.of("jpa"), LocalDate.of(2026, 8, 3));
    }

    private void saveLog(String title, String summary, String note,
            String categoryName, List<String> tags, LocalDate date) {
        Category category = categoryRepository.findByNameKey(Category.toKey(categoryName))
                .orElseGet(() -> categoryRepository.save(new Category(categoryName)));
        studyLogRepository.save(new StudyLog(title, date, LocalTime.of(20, 0), LocalTime.of(21, 0),
                category, new LinkedHashSet<>(tags), summary, note));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        return entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
    }

    private StudyLog sample(String title, Category category) {
        return new StudyLog(title, LocalDate.of(2026, 8, 3),
                LocalTime.of(20, 0), LocalTime.of(21, 0),
                category, new LinkedHashSet<>(List.of("jpa")), "요약", "# 노트");
    }
}
