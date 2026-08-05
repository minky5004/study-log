package com.minky.studylog.web.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 값을 화면 문구로 바꾸는 규칙을 한곳에 모은다.
 * <p>
 * 세션 한 건과 그날 합계가 같은 값을 다르게 적으면 상자 안에서 바로 어긋나 보인다 —
 * 두 자리에 같은 포맷을 복사해 두면 한쪽만 고칠 경로가 생기므로 진입점을 하나로 둔다.
 */
final class DisplayText {

    /**
     * 요일까지 한국어로 고정한다. 템플릿에서 포맷하면 요청 로케일을 따라가,
     * 브라우저 언어가 한국어가 아닌 방문자에게 요일만 영어로 섞여 나온다.
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);

    private DisplayText() {
    }

    static String date(LocalDate date) {
        return date.format(DATE_FORMAT);
    }

    /** 화면에 분 단위 총량을 그대로 내보내지 않는다 — 읽는 사람이 시간으로 환산할 일이 없게. */
    static String duration(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + "분";
        }
        return rest == 0 ? hours + "시간" : hours + "시간 " + rest + "분";
    }
}
