package com.minky.studylog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyLogTest {

    private StudyLog withTags(SequencedSet<String> tags) {
        return new StudyLog("제목", LocalDate.of(2026, 8, 3),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new Category("Spring"), tags, "요약", "노트");
    }

    /**
     * 이 테스트만으로는 순서가 지켜진다고 말할 수 없다 — DB 를 거치지 않아 필드에 담아 둔
     * 컬렉션을 그대로 되읽을 뿐이다. 왕복 쪽 그물은
     * {@code StudyLogRepositoryTest.keepsTagOrderAcrossRoundTrip} 이 맡는다.
     */
    @Test
    @DisplayName("태그는 입력 순서 그대로 노출 — 화면마다 순서가 흔들리지 않게")
    void keepsTagInsertionOrder() {
        LinkedHashSet<String> tags =
                new LinkedHashSet<>(List.of("spring", "jpa", "트랜잭션", "큐", "자료구조"));

        assertThat(withTags(tags).getTags())
                .containsExactly("spring", "jpa", "트랜잭션", "큐", "자료구조");
    }

    @Test
    @DisplayName("반환한 태그 집합은 수정 불가 · 원본에 영향 없음")
    void returnsUnmodifiableCopy() {
        StudyLog log = withTags(new LinkedHashSet<>(List.of("jpa")));

        assertThatThrownBy(() -> log.getTags().add("추가"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(log.getTags()).containsExactly("jpa");
    }

    @Test
    @DisplayName("태그 없이 생성해도 빈 집합")
    void handlesNullTags() {
        assertThat(withTags(null).getTags()).isEmpty();
    }
}
