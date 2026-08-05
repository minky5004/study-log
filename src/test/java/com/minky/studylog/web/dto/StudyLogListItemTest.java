package com.minky.studylog.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyLogListItemTest {

    @Test
    @DisplayName("1시간 이상은 시·분 함께 표기")
    void formatsHoursAndMinutes() {
        assertThat(item(150).durationText()).isEqualTo("2시간 30분");
    }

    @Test
    @DisplayName("정각이면 분을 붙이지 않음")
    void omitsZeroMinutes() {
        assertThat(item(120).durationText()).isEqualTo("2시간");
    }

    @Test
    @DisplayName("1시간 미만은 분만 표기")
    void formatsMinutesOnly() {
        assertThat(item(45).durationText()).isEqualTo("45분");
    }

    @Test
    @DisplayName("자정 넘김 최장 세션도 시간으로 표기 — 1440분 같은 환산 필요한 값 노출 금지")
    void formatsFullDay() {
        assertThat(item(1439).durationText()).isEqualTo("23시간 59분");
    }

    @Test
    @DisplayName("엔티티 변환에서 태그 입력 순서 보존 · 트랜잭션 밖으로 엔티티를 내보내지 않음")
    void copiesTagsInOrder() {
        StudyLog log = new StudyLog("트랜잭션 격리 수준", LocalDate.of(2026, 8, 3),
                LocalTime.of(23, 0), LocalTime.of(1, 0), new Category("Spring"),
                new LinkedHashSet<>(List.of("트랜잭션", "jpa", "인덱스")), "요약", "# 노트");

        StudyLogListItem converted = StudyLogListItem.from(log);

        assertThat(converted.tags()).containsExactly("트랜잭션", "jpa", "인덱스");
        assertThat(converted.categoryName()).isEqualTo("Spring");
        assertThat(converted.durationText()).isEqualTo("2시간");
    }

    private StudyLogListItem item(int durationMinutes) {
        return new StudyLogListItem(1L, "제목", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0), durationMinutes,
                "Spring", List.of(), "요약");
    }
}
