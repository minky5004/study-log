package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TagNormalizerTest {

    @Test
    @DisplayName("앞뒤 공백 제거 · 소문자 변환")
    void trimsAndLowercases() {
        assertThat(TagNormalizer.normalize("  JPA ")).isEqualTo("jpa");
    }

    @Test
    @DisplayName("가운데 연속 공백은 1칸으로")
    void collapsesInnerWhitespace() {
        assertThat(TagNormalizer.normalize("자료   구조")).isEqualTo("자료 구조");
    }

    @Test
    @DisplayName("탭·개행도 공백 1칸으로")
    void collapsesTabsAndNewlines() {
        assertThat(TagNormalizer.normalize("자료\t\n구조")).isEqualTo("자료 구조");
    }

    @Test
    @DisplayName("쉼표 구분 입력을 집합으로 · 대소문자 다른 중복 제거")
    void splitsAndDeduplicates() {
        assertThat(TagNormalizer.normalizeAll("Spring, spring , 큐,, 큐"))
                .containsExactly("spring", "큐");
    }

    @Test
    @DisplayName("입력 순서 보존 — 태그 표시 순서가 입력과 달라지지 않게")
    void preservesInputOrder() {
        assertThat(TagNormalizer.normalizeAll("트랜잭션, jpa, 인덱스"))
                .containsExactly("트랜잭션", "jpa", "인덱스");
    }

    @Test
    @DisplayName("null · 빈 문자열은 빈 집합")
    void handlesEmpty() {
        assertThat(TagNormalizer.normalizeAll(null)).isEmpty();
        assertThat(TagNormalizer.normalizeAll("   ")).isEmpty();
    }

    @Test
    @DisplayName("터키어 로캘에서도 I 는 i 로 — 배포 환경 로캘에 따라 태그가 갈라지지 않게")
    void lowercasesWithRootLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(TagNormalizer.normalize("INDEX")).isEqualTo("index");
        } finally {
            Locale.setDefault(original);
        }
    }
}
