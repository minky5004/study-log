package com.minky.studylog.repository;

import com.minky.studylog.domain.StudyLog;
import java.time.LocalDate;
import java.util.List;
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
     * 목록 한 페이지를 분야와 함께 읽는다. 조건이 전부 {@code null} 이면 전체 조회라
     * 무조건 목록과 검색 결과가 같은 경로를 탄다.
     *
     * <p>{@code join fetch} 대상이 {@code @ManyToOne} 이라 페이징과 함께 써도 메모리 페이징으로
     * 떨어지지 않는다. 태그는 컬렉션이라 fetch join 하지 않는다 — 카티전 곱과 메모리 페이징을
     * 부르므로 {@code @BatchSize} 로 묶어 읽는 편이 낫다.
     *
     * <p>정렬은 {@code Pageable} 이 소유한다. 쿼리에 {@code order by} 를 박으면 중복된다.
     *
     * <p><b>두 쿼리의 조건은 함께 움직여야 한다.</b> count 가 본문보다 넓게 세면 총 건수가
     * 부풀고, 목록 화면의 마지막 페이지 리다이렉트가 멈춰 빈 페이지 링크가 남는다.
     *
     * <p>분야는 표시 이름이 아니라 정규화 키로 맞춘다 — 키 파생은
     * {@link com.minky.studylog.domain.Category#toKey}가 소유하고 공백 축약까지 포함하므로
     * SQL 의 {@code lower()} 로 대신하면 붙여넣은 전각 공백이 든 분야명이 걸리지 않는다.
     * 키워드는 표기를 보존할 대상이 아니라 {@code lower()} 로 족하다.
     *
     * <p>{@code coalesce} 는 요약·노트가 비어도 제목 일치 기록이 남게 한다 — 세 칸을
     * 이어붙여 한 번에 훑는 형태로 바꾸면 빈 칸 하나가 행 전체를 떨어뜨린다.
     *
     * <p>키워드는 이미 {@code %} 로 감싸고 특수문자를 이스케이프한 <b>패턴</b>으로 받는다 —
     * 감싸기를 쿼리 쪽 {@code concat} 에 두면 이스케이프한 자리와 갈라져 한쪽만 고치는 경로가
     * 생긴다. {@code escape} 문자는 서비스의 이스케이프 규칙과 짝이다.
     */
    @Query(value = """
            select l from StudyLog l
            join fetch l.category c
            where (:keywordPattern is null
                   or lower(l.title) like lower(:keywordPattern) escape '\\'
                   or lower(coalesce(l.summary, '')) like lower(:keywordPattern) escape '\\'
                   or lower(coalesce(l.note, '')) like lower(:keywordPattern) escape '\\')
              and (:categoryKey is null or c.nameKey = :categoryKey)
              and (:tag is null or :tag member of l.tags)
              and (:from is null or l.studyDate >= :from)
              and (:to is null or l.studyDate <= :to)
            """,
            countQuery = """
            select count(l) from StudyLog l
            where (:keywordPattern is null
                   or lower(l.title) like lower(:keywordPattern) escape '\\'
                   or lower(coalesce(l.summary, '')) like lower(:keywordPattern) escape '\\'
                   or lower(coalesce(l.note, '')) like lower(:keywordPattern) escape '\\')
              and (:categoryKey is null or l.category.nameKey = :categoryKey)
              and (:tag is null or :tag member of l.tags)
              and (:from is null or l.studyDate >= :from)
              and (:to is null or l.studyDate <= :to)
            """)
    Page<StudyLog> search(@Param("keywordPattern") String keywordPattern,
                          @Param("categoryKey") String categoryKey,
                          @Param("tag") String tag,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          Pageable pageable);

    /**
     * 입력 제안에 쓸 태그. 많이 쓴 것을 앞에 둬야 datalist 를 열자마자 보이는 몇 개가 실제로
     * 고를 것들이다 — 이름순이면 자주 쓰는 태그가 알파벳 뒤쪽에 묻힌다.
     *
     * <p>빈도가 같으면 이름으로 가른다. 동률의 순서를 정하지 않으면 DB 가 돌려주는 순서에 맡겨져
     * 같은 데이터에서도 화면마다 제안 차례가 달라진다.
     */
    @Query("select t from StudyLog l join l.tags t group by t order by count(t) desc, t asc")
    List<String> findDistinctTagsByUsage();
}
