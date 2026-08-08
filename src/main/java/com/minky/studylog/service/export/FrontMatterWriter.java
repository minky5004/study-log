package com.minky.studylog.service.export;

import com.minky.studylog.domain.StudyLog;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 기록 하나를 YAML 프론트매터 + 노트 원문 한 덩어리로 만든다.
 * <p>
 * 내보낸 파일은 사용자 vault 에 남아 다음에 내보낸 파일과 나란히 놓이므로, 키 이름과 따옴표
 * 유무는 편의가 아니라 계약이다. {@code date} 와 {@code durationMinutes} 만 따옴표를 벗기는
 * 것은 옵시디언 Dataview 가 날짜·숫자로 읽어야 정렬과 범위 질의가 성립하기 때문.
 */
@Component
public class FrontMatterWriter {

    private static final String DELIMITER = "---\n";

    public String write(StudyLog log) {
        StringBuilder out = new StringBuilder(DELIMITER);
        appendQuoted(out, "title", log.getTitle());
        out.append("date: ").append(log.getStudyDate()).append('\n');
        appendQuoted(out, "start", log.getStartTime().toString());
        appendQuoted(out, "end", log.getEndTime().toString());
        out.append("durationMinutes: ").append(log.getDurationMinutes()).append('\n');
        appendQuoted(out, "category", log.getCategory().getName());
        out.append("tags: ").append(tagArray(log)).append('\n');
        // 빈 요약을 `summary: ""` 로 내보내면 가져오기가 그것을 값으로 받아, 요약 없던 기록이
        // 빈 문자열 요약을 가진 기록으로 되살아난다
        if (hasText(log.getSummary())) {
            appendQuoted(out, "summary", log.getSummary());
        }
        out.append(DELIMITER);

        // 프론트매터 파싱은 "첫 --- 다음의 첫 ---" 까지라 본문의 구분선은 여기 걸리지 않는다
        if (log.getNote() != null) {
            out.append(log.getNote().replace("\r\n", "\n").replace("\r", "\n"));
        }
        return out.toString();
    }

    /**
     * 태그는 {@code Set} 이라 저장 왕복에서 순회 순서가 흔들린다. 정렬하지 않으면 같은 기록을
     * 두 번 내보낸 것만으로 vault 에 diff 가 남는다.
     */
    private static String tagArray(StudyLog log) {
        return log.getTags().stream().sorted()
                .map(tag -> '"' + escape(tag) + '"')
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static void appendQuoted(StringBuilder out, String key, String value) {
        out.append(key).append(": \"").append(escape(value)).append("\"\n");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
