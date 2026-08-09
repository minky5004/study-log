package com.minky.studylog.service.importer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 마크다운 한 편을 값으로 읽는다. YAML 라이브러리를 들이지 않는 것은 다루는 키가 여덟이고
 * 나머지는 통째로 무시하면 되기 때문 — 의존성 하나가 파서 200줄보다 싸지 않다.
 * <p>
 * 입력 계약은 {@link com.minky.studylog.service.export.FrontMatterWriter} 의 출력이지만,
 * 남의 vault 에서 온 노트도 받는다. 옵시디언은 태그를 블록 리스트로 쓰고 편집기에 따라
 * BOM·CRLF·따옴표 없는 값이 섞인다.
 */
@Component
public class FrontMatterParser {

    private static final String DELIMITER = "---";
    private static final String DEFAULT_CATEGORY = "미분류";

    public ParsedNote parse(String content) {
        List<String> lines = normalize(content).lines().toList();
        if (lines.isEmpty() || !DELIMITER.equals(lines.getFirst())) {
            throw new ImportFormatException("프론트매터 없음 — 첫 줄이 --- 이어야 합니다");
        }

        int closing = lines.subList(1, lines.size()).indexOf(DELIMITER) + 1;
        if (closing == 0) {
            throw new ImportFormatException("프론트매터가 닫히지 않음 — 끝에 --- 이 없습니다");
        }

        // 순서가 곧 실패 사유의 우선순위다 — 제목도 날짜도 없는 파일에 시각을 먼저 나무라면
        // 사용자는 시각만 채워 다시 올린다
        Map<String, String> keys = readKeys(lines.subList(1, closing));
        String title = required(keys, "title");
        LocalDate date = parsed(required(keys, "date"), "date", LocalDate::parse);
        LocalTime start = time(keys, "start");
        LocalTime end = time(keys, "end");

        // 도메인이 같은 시각을 거부한다 — 0분과 24시간을 구별할 수 없어서. 여기서 걸러야
        // 화면의 실패 사유가 도메인 예외 문구가 아니라 파일을 고칠 말이 된다
        if (start.equals(end)) {
            throw new ImportFormatException("start 와 end 가 같음 — 0분과 24시간을 구별할 수 없습니다");
        }
        return new ParsedNote(title, date, start, end,
                keys.getOrDefault("category", DEFAULT_CATEGORY),
                readTags(lines.subList(1, closing)),
                keys.get("summary"),
                body(lines, closing));
    }

    /** BOM 은 편집기가 붙이고 CRLF 는 윈도우가 붙인다. 둘 다 값의 일부가 아니다. */
    private static String normalize(String content) {
        String stripped = content.startsWith("﻿") ? content.substring(1) : content;
        return stripped.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * 모르는 키는 그대로 담아 두고 쓰지 않는다 — 남의 vault 의 {@code aliases} {@code cssclass}
     * 에 걸려 실패하면 이관이 파일 하나 단위로 막힌다.
     */
    private static Map<String, String> readKeys(List<String> frontMatter) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (String line : frontMatter) {
            int colon = line.indexOf(':');
            // 들여쓴 줄은 블록 리스트의 항목이라 키가 아니다
            if (colon <= 0 || !line.equals(line.stripLeading())) {
                continue;
            }
            String value = unquote(line.substring(colon + 1).strip());
            if (!value.isEmpty()) {
                keys.putIfAbsent(line.substring(0, colon).strip(), value);
            }
        }
        return keys;
    }

    /**
     * 세 표기를 모두 받는다 — 인라인 배열({@code [a, b]}) · 블록 리스트 · 쉼표 스칼라
     * ({@code tags: a, b}). 모르는 표기를 <b>조용히 빈 집합으로</b> 두면 태그만 사라진 기록이
     * 실패 표에도 뜨지 않은 채 들어간다.
     */
    private static Set<String> readTags(List<String> frontMatter) {
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < frontMatter.size(); i++) {
            String line = frontMatter.get(i);
            if (!line.equals(line.stripLeading()) || !line.stripTrailing().startsWith("tags:")) {
                continue;
            }
            String inline = line.substring(line.indexOf(':') + 1).strip();
            if (inline.isEmpty()) {
                addAll(tags, blockItems(frontMatter, i + 1));
            } else {
                addAll(tags, inline.replaceAll("^\\[|]$", "").split(","));
            }
            break;
        }
        return tags;
    }

    /** 들여쓰기는 따지지 않는다 — 손으로 쓴 vault 는 {@code - } 를 첫 칸에 두는 쪽이 흔하다. */
    private static String[] blockItems(List<String> frontMatter, int from) {
        List<String> items = new ArrayList<>();
        for (int i = from; i < frontMatter.size(); i++) {
            String item = frontMatter.get(i).strip();
            if (!item.startsWith("- ")) {
                break;
            }
            items.add(item.substring(2));
        }
        return items.toArray(String[]::new);
    }

    private static void addAll(Set<String> tags, String[] values) {
        for (String value : values) {
            String tag = unquote(value.strip());
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
    }

    /** 내보내기가 넣은 이스케이프를 그대로 되돌린다. 순서를 뒤집으면 `\\"` 가 `\"` 로 뭉개진다. */
    private static String unquote(String value) {
        if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        String inner = value.substring(1, value.length() - 1);
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            boolean escaped = c == '\\' && i + 1 < inner.length();
            out.append(escaped ? inner.charAt(++i) : c);
        }
        return out.toString();
    }

    private static String required(Map<String, String> keys, String name) {
        String value = keys.get(name);
        if (value == null) {
            throw new ImportFormatException("필수 키 없음: " + name);
        }
        return value;
    }

    /**
     * 시각 누락은 형식 오류로 돌린다. duration 0 으로 받으려면 두 컬럼을 nullable 로 열어야 하고,
     * 그 null 은 통계·목록·내보내기까지 번진다.
     */
    private static LocalTime time(Map<String, String> keys, String name) {
        String value = keys.get(name);
        if (value == null) {
            throw new ImportFormatException("시작·종료 시각 없음 — start 와 end 가 필요합니다");
        }
        return parsed(value, name, LocalTime::parse);
    }

    private static <T> T parsed(String value, String name, Function<String, T> reader) {
        try {
            return reader.apply(value);
        } catch (DateTimeParseException e) {
            throw new ImportFormatException(name + " 형식 오류: " + value);
        }
    }

    /** 프론트매터만 있는 노트는 본문이 빈 문자열이 아니라 없는 것이다. */
    private static String body(List<String> lines, int closing) {
        String body = String.join("\n", lines.subList(closing + 1, lines.size()));
        return body.isBlank() ? null : body;
    }
}
