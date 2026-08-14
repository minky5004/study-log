package com.minky.studylog.service;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.web.dto.StudyLogDetail;
import com.minky.studylog.web.dto.StudyLogForm;
import com.minky.studylog.web.dto.StudyLogListItem;
import com.minky.studylog.web.dto.StudyLogSearchCond;
import java.util.Locale;
import java.util.function.UnaryOperator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyLogService {

    /**
     * 최신순. 목록 화면과 홈이 같은 값을 가리켜야 "최근" 이 두 뜻으로 갈리지 않는다 —
     * 홈에 뜬 다섯 건이 목록 첫 다섯 건과 다르면 어느 쪽이 맞는지 읽는 사람이 정해야 한다.
     *
     * <p>{@code id} 를 마지막 키로 두는 것은 날짜·시각이 같은 기록의 순서가 조회마다 흔들리지
     * 않게 하기 위해서 — 목록에서는 그 흔들림이 페이지 경계에서 같은 기록의 중복·누락이 된다.
     */
    public static final Sort LATEST_FIRST =
            Sort.by(Sort.Direction.DESC, "studyDate", "startTime", "id");

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
    public Page<StudyLogListItem> findAll(StudyLogSearchCond cond, Pageable pageable) {
        return studyLogRepository.search(
                        likePattern(cond.getKeyword()),
                        // 분야·태그는 저장 형태로 맞춰 넘긴다 — 화면 입력은 표기가 흔들린다
                        map(cond.getCategoryName(), Category::toKey),
                        map(cond.getTag(), TagNormalizer::normalize),
                        cond.getFrom(),
                        cond.getTo(),
                        pageable)
                .map(StudyLogListItem::from);
    }

    /**
     * 키워드를 LIKE 패턴으로 감싼다. 특수문자를 먼저 이스케이프하지 않으면 사용자가 친 글자가
     * 와일드카드로 새어 나간다 — {@code _} 한 글자가 전건을 걸고 {@code 100%} 가 {@code 100} 으로
     * 시작하는 모든 기록을 건다. 역슬래시를 먼저 바꾸는 순서라 뒤에 붙는 이스케이프가 다시
     * 이스케이프되지 않는다.
     *
     * <p><b>소문자화가 여기 있는 이유는 방언이다.</b> 쿼리 쪽에서 {@code lower(:keywordPattern)}
     * 으로 감싸면 검색어가 없는 전체 조회에서 PostgreSQL 이 타입 없는 {@code null} 을
     * {@code bytea} 로 추론해 {@code function lower(bytea) does not exist} 로 목록이 통째로
     * 죽는다. H2 는 {@code MODE=PostgreSQL} 이어도 이것을 통과시킨다. 파라미터를 함수 밖으로
     * 빼면 추론할 것이 없어지고, 덤으로 행마다 같은 패턴을 세 번 소문자화하던 것도 사라진다.
     * 컬럼 쪽 {@code lower()} 는 그대로 남아 대소문자 무시는 유지된다.
     */
    private static String likePattern(String raw) {
        String collapsed = blankToNull(raw);
        if (collapsed == null) {
            return null;
        }
        String escaped = collapsed.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return ("%" + escaped + "%").toLowerCase(Locale.ROOT);
    }

    /**
     * 빈 조건을 {@code null} 로 접는다. 쿼리가 {@code :param is null} 로 조건 유무를 가리므로
     * 빈 문자열이 그대로 가면 아무것도 걸리지 않는 조건이 켜진다.
     *
     * <p>공백 축약에 유니코드 인식 {@code (?U)} 를 붙이는 것은 {@code String.isBlank()} 와
     * {@code trim()} 이 비분리 공백(U+00A0)을 보지 못하기 때문 — 문서에서 붙여넣은 키워드가
     * 조용히 무결과가 된다. 분야·태그는 각자의 정규화가 이미 같은 처리를 한다.
     */
    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.replaceAll("(?U)\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** 정규화 결과가 비는 입력(공백뿐인 태그 등)도 조건 없음으로 접는다. */
    private static String map(String raw, UnaryOperator<String> normalizer) {
        String collapsed = blankToNull(raw);
        return collapsed == null ? null : blankToNull(normalizer.apply(collapsed));
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
