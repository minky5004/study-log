package com.minky.studylog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class StudyLogRepositoryTest {

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
}
