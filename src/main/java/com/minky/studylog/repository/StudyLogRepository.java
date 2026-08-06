package com.minky.studylog.repository;

import com.minky.studylog.domain.StudyLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {

    /** 분야는 상세 머리글에 늘 함께 나가므로 한 번에 가져온다. */
    @Query("select l from StudyLog l join fetch l.category where l.id = :id")
    Optional<StudyLog> findWithCategoryById(@Param("id") Long id);
}
