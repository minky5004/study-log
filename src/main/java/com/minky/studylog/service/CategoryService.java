package com.minky.studylog.service;

import com.minky.studylog.domain.Category;
import com.minky.studylog.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 분야는 표시 이름을 유지해야 하므로 최초 등록 표기를 그대로 저장하고, 조회는 정규화 키로 한다.
     * 태그({@link TagNormalizer})가 소문자로 통일해 저장하는 것과 규칙이 다른 이유가 여기 있다.
     */
    @Transactional
    public Category resolve(String rawName) {
        String key = Category.toKey(rawName);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("분야는 필수");
        }
        return categoryRepository.findByNameKey(key)
                .orElseGet(() -> categoryRepository.save(new Category(rawName)));
    }
}
