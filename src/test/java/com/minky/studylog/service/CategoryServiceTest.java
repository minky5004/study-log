package com.minky.studylog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minky.studylog.domain.Category;
import com.minky.studylog.repository.CategoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CategoryServiceTest {

    CategoryRepository repository;
    CategoryService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CategoryRepository.class);
        service = new CategoryService(repository);
    }

    @Test
    @DisplayName("대소문자만 다른 분야는 기존 것을 재사용 — 통계가 갈라지지 않게")
    void reusesExistingIgnoringCase() {
        Category existing = new Category("Spring");
        Mockito.when(repository.findByNameKey("spring")).thenReturn(Optional.of(existing));

        assertThat(service.resolve("  spring ")).isSameAs(existing);
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    @DisplayName("없는 분야는 최초 입력 표기 그대로 생성 · 조회 키는 소문자")
    void createsWithFirstSeenCasing() {
        Mockito.when(repository.findByNameKey("spring")).thenReturn(Optional.empty());
        Mockito.when(repository.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        service.resolve("  Spring  ");

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Spring");
        assertThat(captor.getValue().getNameKey()).isEqualTo("spring");
    }

    @Test
    @DisplayName("이름 가운데 연속 공백도 조회 키에서 1칸 — 오타성 중복 분야 생성 방지")
    void collapsesInnerWhitespaceForKey() {
        Mockito.when(repository.findByNameKey("자료 구조"))
                .thenReturn(Optional.of(new Category("자료 구조")));

        assertThat(service.resolve("자료   구조").getName()).isEqualTo("자료 구조");
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    @DisplayName("빈 분야는 거부")
    void rejectsBlank() {
        assertThatThrownBy(() -> service.resolve("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 분야는 거부 — 리포지토리를 건드리지 않고")
    void rejectsNull() {
        assertThatThrownBy(() -> service.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
        Mockito.verifyNoInteractions(repository);
    }
}
