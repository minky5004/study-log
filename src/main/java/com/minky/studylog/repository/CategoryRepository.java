package com.minky.studylog.repository;

import com.minky.studylog.domain.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 조회 키는 {@link Category#toKey(String)} 로 만든다 — 정확 일치라 unique 인덱스를 그대로 탄다. */
    Optional<Category> findByNameKey(String nameKey);
}
