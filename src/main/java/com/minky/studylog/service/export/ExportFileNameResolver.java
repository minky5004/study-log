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
    private static final Pattern TRAILING_DOT_OR_SPACE = Pattern.compile("[. ]+$");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");
    private static final String EXTENSION = ".md";

    /** 사이드바에서 읽히는 길이. 파일시스템이 아니라 사람이 정하는 한계다. */
    private static final int MAX_TITLE_CHARS = 100;

    /**
     * 이름 한 요소의 바이트 예산. ext4·APFS 의 한계가 255<b>바이트</b>라 글자 수로 재면
     * 한글 제목이 조용히 넘긴다 — 100자면 300바이트다. 압축을 푸는 폴더 경로 몫으로 55바이트를
     * 남긴다.
     */
    private static final int MAX_NAME_BYTES = 200;

    private final Set<String> taken = new HashSet<>();

    public String resolve(StudyLog log) {
        String date = log.getStudyDate().toString();
        String title = sanitize(log.getTitle());
        String time = "-" + log.getStartTime().format(HHMM);

        String candidate = assemble(date, title, "");
        if (claim(candidate)) {
            return candidate;
        }
        candidate = assemble(date, title, time);
        if (claim(candidate)) {
            return candidate;
        }
        for (int sequence = 2; ; sequence++) {
            candidate = assemble(date, title, time + "-" + sequence);
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

    /**
     * 제목 몫을 <b>접미사를 정한 뒤에</b> 계산한다. 접미사 자리를 상수로 미리 떼어 두면 그 상수가
     * 가정한 자릿수를 순번이 넘는 순간 예산이 조용히 깨진다.
     */
    private static String assemble(String date, String title, String suffix) {
        int budget = MAX_NAME_BYTES - utf8Length(date) - utf8Length(suffix) - EXTENSION.length();
        String fitted = trimTrailing(truncateToBytes(title, budget - 1)); // 1 은 날짜 뒤 붙임표

        // 제목이 통째로 지워져도 날짜는 남긴다 — 확장자만 남은 이름은 숨김 파일로 잡힌다
        return fitted.isEmpty()
                ? date + suffix + EXTENSION
                : date + "-" + fitted + suffix + EXTENSION;
    }

    private static String sanitize(String title) {
        String cleaned = ILLEGAL.matcher(title.strip()).replaceAll("_");
        return cleaned.codePointCount(0, cleaned.length()) <= MAX_TITLE_CHARS
                ? cleaned
                : cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_TITLE_CHARS));
    }

    /**
     * 코드포인트 단위로 자른다. {@code substring} 으로 UTF-16 단위를 끊으면 이모지 한 자가
     * 짝 잃은 서러게이트로 남아 엔트리 이름에 {@code ?} 가 박힌다.
     */
    private static String truncateToBytes(String value, int budget) {
        StringBuilder fitted = new StringBuilder();
        int used = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            int width = utf8Width(codePoint);
            if (used + width > budget) {
                break;
            }
            fitted.appendCodePoint(codePoint);
            used += width;
            i += Character.charCount(codePoint);
        }
        return fitted.toString();
    }

    /** 끝의 마침표·공백은 윈도우가 이름에서 떼어 내 저장해, 압축을 풀 때 이름이 어긋난다. */
    private static String trimTrailing(String value) {
        return TRAILING_DOT_OR_SPACE.matcher(value).replaceAll("");
    }

    private static int utf8Length(String value) {
        int length = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            length += utf8Width(codePoint);
            i += Character.charCount(codePoint);
        }
        return length;
    }

    private static int utf8Width(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
