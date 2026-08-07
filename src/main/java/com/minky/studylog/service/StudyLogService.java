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

    /**
     * 수정 폼 프리필. 노트는 렌더링을 거치지 않은 원본으로 돌려준다 —
     * 상세가 쓰는 {@link StudyLogDetail} 의 HTML 을 넣으면 편집 한 번에 원문이 사라진다.
     */
    @Transactional(readOnly = true)
    public StudyLogForm toForm(Long id) {
        StudyLog log = studyLogRepository.findWithCategoryById(id)
                .orElseThrow(() -> new StudyLogNotFoundException(id));

        StudyLogForm form = new StudyLogForm();
        form.setId(log.getId());
        form.setTitle(log.getTitle());
        form.setStudyDate(log.getStudyDate());
        form.setStartTime(log.getStartTime());
        form.setEndTime(log.getEndTime());
        form.setCategoryName(log.getCategory().getName());
        // 쉼표 뒤 공백을 넣지 않는 것은 프리필이 입력보다 길어지지 않게 하기 위해서 —
        // 태그 상한을 채운 기록이 폼 입력 길이 상한에 걸려 수정 자체가 막힌다
        form.setTagsCsv(String.join(",", log.getTags()));
        form.setSummary(log.getSummary());
        form.setNote(log.getNote());
        return form;
    }

    @Transactional
    public void update(Long id, StudyLogForm form) {
        StudyLog log = studyLogRepository.findById(id)
                .orElseThrow(() -> new StudyLogNotFoundException(id));
        log.update(
                form.getTitle().trim(),
                form.getStudyDate(),
                form.getStartTime(),
                form.getEndTime(),
                categoryService.resolve(form.getCategoryName()),
                TagNormalizer.normalizeAll(form.getTagsCsv()),
                form.getSummary(),
                form.getNote());
    }

    @Transactional
    public void delete(Long id) {
        StudyLog log = studyLogRepository.findById(id)
                .orElseThrow(() -> new StudyLogNotFoundException(id));
        studyLogRepository.delete(log);
    }
}
