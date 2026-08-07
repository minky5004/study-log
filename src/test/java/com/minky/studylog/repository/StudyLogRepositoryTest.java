package com.minky.studylog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class StudyLogRepositoryTest {

    /** 컨트롤러가 목록에 쓰는 크기·정렬 — 계측이 화면과 다른 조건을 재면 회귀를 놓친다. */
    private static final int PAGE_SIZE = 20;
    private static final Sort LIST_SORT =
            Sort.by(Sort.Direction.DESC, "studyDate", "startTime", "id");

    @Autowired StudyLogRepository studyLogRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TestEntityManager entityManager;

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
        assertThat(found.getTags()).containsExactlyInAnyOrder("jpa", "트랜잭션");
        assertThat(found.getCategory().getName()).isEqualTo("Spring");
        assertThat(found.getNote()).isEqualTo("# 노트");
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
        stats.clear();

        Page<StudyLog> page = studyLogRepository.findPageWithCategory(
                PageRequest.of(0, PAGE_SIZE, LIST_SORT));
        // 화면에 나가는 접근을 그대로 재현한다 — 목록 DTO 가 분야명과 태그를 모두 읽는다
        page.getContent().forEach(log -> {
            log.getCategory().getName();
            log.getTags().size();
        });

        // 목록 1 + count 1 + 태그 배치 1
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
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
