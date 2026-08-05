package com.minky.studylog.repository;

import com.minky.studylog.domain.StudyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {
}
