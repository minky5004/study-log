package com.minky.studylog.service.importer;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.service.CategoryService;
import com.minky.studylog.service.TagNormalizer;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노트 하나를 한 트랜잭션에 넣는다.
 * <p>
 * <b>별도 빈인 것이 요점이다.</b> 같은 클래스 안의 메서드를 부르면 프록시를 지나지 않아
 * {@code @Transactional} 이 걸리지 않고, 그러면 {@code CategoryService.resolve} 가 자기
 * 트랜잭션에서 먼저 커밋된다 — 뒤이어 저장이 막히면 아무 기록도 쓰지 않는 분야 행만 영구히
 * 남는다. 분야 관리 화면을 만들지 않기로 했으므로 지울 수단도 없다.
 */
@Component
class NoteWriter {

    /** 엔티티 컬럼 길이와 짝. 넘긴 값을 그대로 저장하면 JDBC 원문이 화면의 실패 사유가 된다. */
    private static final int MAX_TITLE = 200;
    private static final int MAX_SUMMARY = 500;
    private static final int MAX_CATEGORY = 50;
    private static final int MAX_TAG = 50;

    private final StudyLogRepository studyLogRepository;
    private final CategoryService categoryService;

    NoteWriter(StudyLogRepository studyLogRepository, CategoryService categoryService) {
        this.studyLogRepository = studyLogRepository;
        this.categoryService = categoryService;
    }

    /** @return 새로 넣었으면 {@code true}, 이미 있는 기록이라 건너뛰었으면 {@code false} */
    @Transactional
    boolean write(ParsedNote note) {
        if (studyLogRepository.existsByStudyDateAndTitleIgnoreCaseAndStartTime(
                note.date(), note.title(), note.start())) {
            return false;
        }
        Set<String> tags = normalized(note.tags());
        checkLengths(note, tags);

        studyLogRepository.save(new StudyLog(
                note.title(),
                note.date(),
                note.start(),
                note.end(),
                categoryService.resolve(note.category()),
                tags,
                note.summary(),
                note.body()));
        return true;
    }

    private static void checkLengths(ParsedNote note, Set<String> tags) {
        check(note.title(), MAX_TITLE, "제목");
        check(note.summary(), MAX_SUMMARY, "요약");
        check(note.category(), MAX_CATEGORY, "분야");
        for (String tag : tags) {
            check(tag, MAX_TAG, "태그");
        }
    }

    private static void check(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new ImportFormatException(label + "이 " + max + "자를 넘음 — " + value.length() + "자");
        }
    }

    /** 태그는 저장 형태가 소문자라 화면 입력과 같은 규칙을 태운다 — 여기만 원문을 남기면 갈라진다. */
    private static Set<String> normalized(Set<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalized = TagNormalizer.normalize(tag);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }
}
