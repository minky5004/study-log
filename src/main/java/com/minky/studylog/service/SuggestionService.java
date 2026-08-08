package com.minky.studylog.service;

import com.minky.studylog.repository.CategoryRepository;
import com.minky.studylog.repository.StudyLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분야·태그 입력 제안. 화면이 리포지토리를 직접 부르지 않게 두는 얇은 층이자, 제안 순서 규칙이
 * 어디에 있는지 가리키는 자리다 — 순서 자체는 쿼리가 소유한다.
 *
 * <p>제안은 사용자가 이미 쓴 표기를 되돌려 주는 것이라 별도 정규화를 하지 않는다.
 * 분야는 최초 등록 표기, 태그는 저장된 소문자 그대로 나간다.
 */
@Service
public class SuggestionService {

    private final CategoryRepository categoryRepository;
    private final StudyLogRepository studyLogRepository;

    public SuggestionService(CategoryRepository categoryRepository,
                             StudyLogRepository studyLogRepository) {
        this.categoryRepository = categoryRepository;
        this.studyLogRepository = studyLogRepository;
    }

    @Transactional(readOnly = true)
    public List<String> categoryNames() {
        return categoryRepository.findAllNamesOrderedByKey();
    }

    @Transactional(readOnly = true)
    public List<String> tagNames() {
        return studyLogRepository.findDistinctTagsByUsage();
    }
}
