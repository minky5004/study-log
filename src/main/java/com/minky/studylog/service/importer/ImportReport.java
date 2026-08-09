package com.minky.studylog.service.importer;

import java.util.List;

/**
 * 가져오기 한 번의 결과. 셋을 나누는 것은 셋의 뜻이 다르기 때문 — 건너뜀은 이미 있는 기록이라
 * 손댈 것이 없고, 실패는 사용자가 파일을 고쳐 다시 올려야 한다.
 */
public record ImportReport(int succeeded, int skipped, List<Failure> failures) {

    public record Failure(String fileName, String reason) {
    }

    public int total() {
        return succeeded + skipped + failures.size();
    }

    public boolean empty() {
        return total() == 0;
    }
}
