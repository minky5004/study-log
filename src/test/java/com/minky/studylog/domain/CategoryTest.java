package com.minky.studylog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryTest {

    // 눈에 보이지 않는 문자라 소스에 그대로 넣지 않는다
    private static final String NBSP = Character.toString(0x00A0);
    private static final String IDEOGRAPHIC_SPACE = Character.toString(0x3000);

    @Test
    @DisplayName("표시 이름은 trim · 연속 공백 1칸 · 조회 키는 그 소문자")
    void derivesKeyFromDisplayName() {
        Category category = new Category("  Spring   Boot  ");

        assertThat(category.getName()).isEqualTo("Spring Boot");
        assertThat(category.getNameKey()).isEqualTo("spring boot");
    }

    @Test
    @DisplayName("붙여넣기로 들어오는 비분리 공백도 일반 공백과 같은 키 — 같은 분야가 둘로 갈라지지 않게")
    void treatsNonBreakingSpaceAsSpace() {
        assertThat(Category.toKey("Spring" + NBSP + "Boot")).isEqualTo("spring boot");
        assertThat(Category.toKey("Spring" + IDEOGRAPHIC_SPACE + "Boot")).isEqualTo("spring boot");
        assertThat(new Category("Spring" + NBSP + "Boot").getName()).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("비분리 공백만 있는 이름은 빈 키 — 화면에 이름 없는 분야가 생기지 않게")
    void treatsWhitespaceOnlyNameAsEmpty() {
        assertThat(Category.toKey(NBSP)).isEmpty();
        assertThat(Category.toKey(IDEOGRAPHIC_SPACE)).isEmpty();
        assertThat(Category.toKey(null)).isEmpty();
    }

    @Test
    @DisplayName("공백뿐인 이름으로는 생성 불가")
    void rejectsBlankName() {
        assertThatThrownBy(() -> new Category("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Category(NBSP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Category(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("터키어 로캘에서도 I 는 i 로 — 로캘에 따라 unique 키가 달라지지 않게")
    void derivesKeyWithRootLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(Category.toKey("INDEX")).isEqualTo("index");
            assertThat(new Category("INDEX").getNameKey()).isEqualTo("index");
        } finally {
            Locale.setDefault(original);
        }
    }
}
