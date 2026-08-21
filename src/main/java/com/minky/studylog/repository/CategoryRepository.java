package com.minky.studylog.repository;

import com.minky.studylog.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 조회 키는 {@link Category#toKey(String)} 로 만든다 — 정확 일치라 unique 인덱스를 그대로 탄다. */
    Optional<Category> findByNameKey(String nameKey);

    /**
     * 입력 제안에 쓸 분야 표시 이름. 정렬 키를 {@code name} 이 아니라 정규화 컬럼으로 두는 것은
     * 기본 정렬이 대문자를 앞으로 몰아 {@code CS · Spring · algorithm} 순으로 늘어놓기 때문 —
     * 목록에서 눈으로 훑는 순서가 아니다. 이름 자체는 최초 등록 표기 그대로 나간다.
     *
     * <p>기록이 붙은 분야만 싣는다. 분야를 지우는 경로가 없어 오타로 만든 행은 영구히 남는데,
     * 그것을 제안에 두면 다음 입력에서 다시 골라져 재생산된다. 태그 제안이 {@code join l.tags}
     * 로 이미 쓰인 것만 내는 것과 같은 규칙이다.
     *
     * <p>{@code join} 대신 {@code exists} 인 것은 분야당 한 행이 저절로 나와 {@code distinct}
     * 도 {@code group by} 도 필요 없기 때문 — 정렬 규칙이 {@code order by} 한 줄에 남는다.
     */
    @Query("select c.name from Category c "
            + "where exists (select 1 from StudyLog l where l.category = c) "
            + "order by c.nameKey")
    List<String> findUsedNamesOrderedByKey();
}
