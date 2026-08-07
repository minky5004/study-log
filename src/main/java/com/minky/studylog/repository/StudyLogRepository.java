package com.minky.studylog.repository;

import com.minky.studylog.domain.StudyLog;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {

    /** 분야는 상세 머리글에 늘 함께 나가므로 한 번에 가져온다. */
    @Query("select l from StudyLog l join fetch l.category where l.id = :id")
    Optional<StudyLog> findWithCategoryById(@Param("id") Long id);

    /**
     * 목록 한 페이지를 분야와 함께 읽는다. 기본 {@code findAll} 은 행마다 분야를 따로 조회한다.
     *
     * <p>{@code join fetch} 대상이 {@code @ManyToOne} 이라 페이징과 함께 써도 메모리 페이징으로
     * 떨어지지 않는다. 태그는 컬렉션이라 fetch join 하지 않는다 — 카티전 곱과 메모리 페이징을
     * 부르므로 {@code @BatchSize} 로 묶어 읽는 편이 낫다.
     *
     * <p>정렬은 {@code Pageable} 이 소유한다. 쿼리에 {@code order by} 를 박으면 중복된다.
     */
    @Query(value = "select l from StudyLog l join fetch l.category",
            countQuery = "select count(l) from StudyLog l")
    Page<StudyLog> findPageWithCategory(Pageable pageable);
}
