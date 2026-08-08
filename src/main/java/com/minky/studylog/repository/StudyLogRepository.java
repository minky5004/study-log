package com.minky.studylog.repository;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.projection.CategoryTotal;
import com.minky.studylog.repository.projection.DailyTotal;
import com.minky.studylog.repository.projection.StartTimeSlice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
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
     * 내보내기가 전량을 훑는 통로. 오프셋 대신 마지막으로 읽은 식별자를 기준으로 다음 덩어리를
     * 가져온다 — 오프셋은 페이지마다 count 쿼리를 부르고, 훑는 도중 기록이 하나 늘면 그 뒤가
     * 통째로 한 칸씩 밀려 한 건이 ZIP 에서 빠진다.
     *
     * <p>정렬을 쿼리가 소유하므로 {@code Pageable} 이 아니라 {@code Limit} 을 받는다 —
     * {@code Pageable} 의 {@code Sort} 는 여기 박힌 {@code order by} 와 겹친다.
     */
    @Query("select l from StudyLog l join fetch l.category where l.id > :afterId order by l.id asc")
    List<StudyLog> findChunkAfterId(@Param("afterId") long afterId, Limit limit);

    /**
     * 입력 제안에 쓸 태그. 많이 쓴 것을 앞에 둬야 datalist 를 열자마자 보이는 몇 개가 실제로
     * 고를 것들이다 — 이름순이면 자주 쓰는 태그가 알파벳 뒤쪽에 묻힌다.
     *
     * <p>빈도가 같으면 이름으로 가른다. 동률의 순서를 정하지 않으면 DB 가 돌려주는 순서에 맡겨져
     * 같은 데이터에서도 화면마다 제안 차례가 달라진다.
     */
    @Query("select t from StudyLog l join l.tags t group by t order by count(t) desc, t asc")
    List<String> findDistinctTagsByUsage();

    /**
     * 일별 합계. 히트맵과 주·월 추이가 모두 이 결과에서 파생되므로 날짜 단위까지만 DB 가 접는다 —
     * 주·월 버킷팅을 방언(`date_trunc`)에 맡기면 H2 와 PostgreSQL 을 매번 대조해야 한다.
     *
     * <p>{@code durationMinutes} 는 저장 시점 계산값이라 여기서 다시 재지 않는다.
     */
    @Query("""
            select new com.minky.studylog.repository.projection.DailyTotal(
                    l.studyDate, sum(l.durationMinutes))
            from StudyLog l
            where l.studyDate between :from and :to
            group by l.studyDate
            order by l.studyDate asc
            """)
    List<DailyTotal> findDailyTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 분야별 합계. 색 배정이 식별자 기준이라 이름과 함께 묶는다 — 표기가 같아도 다른 분야는 없다. */
    @Query("""
            select new com.minky.studylog.repository.projection.CategoryTotal(
                    c.id, c.name, sum(l.durationMinutes))
            from StudyLog l join l.category c
            group by c.id, c.name
            order by sum(l.durationMinutes) desc, c.name asc
            """)
    List<CategoryTotal> findCategoryTotals();

    /**
     * 시간대 분포의 재료. 시(hour) 추출은 방언마다 달라 자바에 두지만, 시각 단위 합계까지는
     * 표준 {@code group by} 로 접는다 — 접지 않으면 기록 수만큼의 행이 매 요청 앱으로 올라오고
     * 그 수는 매일 쓰는 도구에서 단조 증가한다. 접은 뒤 행 수는 실제로 쓰인 시각 수만큼이다.
     */
    @Query("""
            select new com.minky.studylog.repository.projection.StartTimeSlice(
                    l.startTime, sum(l.durationMinutes))
            from StudyLog l
            group by l.startTime
            """)
    List<StartTimeSlice> findStartTimeSlices();
}
