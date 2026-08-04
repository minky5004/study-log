package com.minky.studylog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class StudyLogRepositoryTest {

    @Autowired StudyLogRepository studyLogRepository;
    @Autowired CategoryRepository categoryRepository;

    @Test
    @DisplayName("자정 넘김 세션이 저장 시점 계산값으로 저장")
    void savesDerivedDuration() {
        Category spring = categoryRepository.save(new Category("Spring"));
        StudyLog saved = studyLogRepository.save(new StudyLog(
                "트랜잭션 격리 수준", LocalDate.of(2026, 8, 3),
                LocalTime.of(23, 0), LocalTime.of(1, 0),
                spring, Set.of("jpa", "트랜잭션"), "격리 수준 4단계 정리", "# 노트"));

        assertThat(saved.getDurationMinutes()).isEqualTo(120);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("분야 이름은 대소문자 무시로 조회")
    void findsCategoryIgnoringCase() {
        categoryRepository.save(new Category("Spring"));
        assertThat(categoryRepository.findByNameIgnoreCase("spring")).isPresent();
    }
}
