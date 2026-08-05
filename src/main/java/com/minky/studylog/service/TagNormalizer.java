package com.minky.studylog.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TagNormalizer {

    private TagNormalizer() {
    }

    /**
     * 태그는 표시 형태를 따로 보존하지 않으므로 소문자로 통일해 저장한다.
     * 소문자 변환에 {@link Locale#ROOT} 를 쓰는 것은 배포 환경 로캘(터키어 등)에 따라
     * 같은 입력이 다른 태그로 갈라지는 것을 막기 위해서.
     * 공백 축약의 {@code (?U)} 는 비분리 공백(U+00A0)·전각 공백(U+3000) — 붙여넣은 태그가
     * 타이핑한 것과 갈라지거나, 보이지 않는 태그가 자동완성 목록에 남는 것을 막는다.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?U)\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** 쉼표 구분 입력을 정규화 집합으로. 입력 순서를 보존하고 빈 값·중복을 버린다. */
    public static LinkedHashSet<String> normalizeAll(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(TagNormalizer::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
