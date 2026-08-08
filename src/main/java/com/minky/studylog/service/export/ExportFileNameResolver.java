package com.minky.studylog.service.export;

import com.minky.studylog.domain.StudyLog;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 기록 하나에 ZIP 엔트리 이름 하나를 배정한다.
 * <p>
 * 이미 쓴 이름은 밖에서 받지 않고 리졸버가 쥔다 — 넘겨받는 형태면 호출자가 반환값을 집합에
 * 도로 넣어야 하고, 그 한 줄을 잊는 순간 {@code ZipOutputStream} 이 중복 엔트리로 압축 도중에
 * 던진다. 그것도 같은 날 같은 제목이 있는 특정 데이터에서만.
 * <p>
 * 그러므로 인스턴스 하나는 내보내기 한 번에 대응한다. 재사용하면 두 번째 압축의 첫 파일부터
 * 헛된 순번이 붙는다.
 */
public class ExportFileNameResolver {

    private static final Pattern ILLEGAL = Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}]");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");
    private static final String EXTENSION = ".md";

    /** {@code -2305-99} 까지 붙어도 넘지 않도록 제목 몫에서 미리 뗀다. */
    private static final int SUFFIX_ROOM = 8;
    private static final int MAX_NAME = 120;
    private static final int MAX_TITLE =
            MAX_NAME - EXTENSION.length() - SUFFIX_ROOM - "yyyy-MM-dd-".length();

    private final Set<String> taken = new HashSet<>();

    public String resolve(StudyLog log) {
        String base = log.getStudyDate() + suffixOfTitle(log.getTitle());

        String candidate = base + EXTENSION;
        if (claim(candidate)) {
            return candidate;
        }
        candidate = base + "-" + log.getStartTime().format(HHMM) + EXTENSION;
        if (claim(candidate)) {
            return candidate;
        }

        String stem = base + "-" + log.getStartTime().format(HHMM);
        for (int sequence = 2; ; sequence++) {
            candidate = stem + "-" + sequence + EXTENSION;
            if (claim(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * 대소문자만 다른 이름도 쓴 것으로 친다. ZIP 자체는 둘을 다른 엔트리로 받지만, 압축을 푸는
     * 곳이 윈도우·맥이면 뒤 파일이 앞 파일을 덮어 기록이 조용히 사라진다.
     */
    private boolean claim(String name) {
        return taken.add(name.toLowerCase(Locale.ROOT));
    }

    private static String suffixOfTitle(String title) {
        String sanitized = ILLEGAL.matcher(title.strip()).replaceAll("_");
        if (sanitized.length() > MAX_TITLE) {
            sanitized = sanitized.substring(0, MAX_TITLE);
        }
        // 끝의 마침표·공백은 윈도우가 이름에서 떼어 내 저장해, 압축을 풀 때 이름이 어긋난다
        sanitized = sanitized.replaceAll("[. ]+$", "");

        // 제목이 통째로 지워져도 날짜는 남긴다 — 확장자만 남은 이름은 숨김 파일로 잡힌다
        return sanitized.isEmpty() ? "" : "-" + sanitized;
    }
}
