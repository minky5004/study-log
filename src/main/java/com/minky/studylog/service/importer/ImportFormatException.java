package com.minky.studylog.service.importer;

/**
 * 파일 하나가 형식 때문에 들어가지 못한 사유. 배치 전체를 죽이지 않고 실패 표의 한 줄이 된다 —
 * 그러므로 사유는 화면에 그대로 나갈 문장이어야 한다.
 */
public class ImportFormatException extends RuntimeException {

    public ImportFormatException(String reason) {
        super(reason);
    }
}
