package com.minky.studylog.service;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.web.dto.StudyLogDetail;
import com.minky.studylog.web.dto.StudyLogForm;
import com.minky.studylog.web.dto.StudyLogListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyLogService {

    private final StudyLogRepository studyLogRepository;
    private final CategoryService categoryService;
    private final MarkdownRenderer markdownRenderer;

    public StudyLogService(StudyLogRepository studyLogRepository, CategoryService categoryService,
                           MarkdownRenderer markdownRenderer) {
        this.studyLogRepository = studyLogRepository;
        this.categoryService = categoryService;
        this.markdownRenderer = markdownRenderer;
    }

    @Transactional
    public Long create(StudyLogForm form) {
        StudyLog log = new StudyLog(
                form.getTitle().trim(),
                form.getStudyDate(),
                form.getStartTime(),
                form.getEndTime(),
                categoryService.resolve(form.getCategoryName()),
                TagNormalizer.normalizeAll(form.getTagsCsv()),
                form.getSummary(),
                form.getNote());
        return studyLogRepository.save(log).getId();
    }

    /**
     * 지연 로딩 필드 접근을 이 트랜잭션 안에서 끝내고 DTO 로 내보낸다 —
     * {@code open-in-view=false} 라 엔티티를 그대로 넘기면 템플릿에서 초기화가 터진다.
     */
    @Transactional(readOnly = true)
    public Page<StudyLogListItem> findAll(Pageable pageable) {
        return studyLogRepository.findAll(pageable).map(StudyLogListItem::from);
    }

    @Transactional(readOnly = true)
    public StudyLogDetail findById(Long id) {
        StudyLog log = studyLogRepository.findWithCategoryById(id)
                .orElseThrow(() -> new StudyLogNotFoundException(id));
        return StudyLogDetail.from(log, markdownRenderer.toSafeHtml(log.getNote()));
    }
}
