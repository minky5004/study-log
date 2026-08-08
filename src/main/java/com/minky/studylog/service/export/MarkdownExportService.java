package com.minky.studylog.service.export;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전체 기록을 마크다운 파일 하나씩 담은 ZIP 으로 흘려보낸다.
 * <p>
 * 폴더 없이 평평하게 둔다 — 분야로 갈라 담으면 vault 에서 분야를 바꿀 때 파일이 움직여야 하고,
 * 거르는 일은 프론트매터 쪽이 더 잘한다.
 */
@Service
public class MarkdownExportService {

    /**
     * 한 번에 읽는 기록 수. 태그는 {@code @BatchSize(50)} 라 이 크기가 곧 덩어리당 태그 쿼리
     * 넷이라는 뜻이다.
     */
    private static final int CHUNK_SIZE = 200;

    private final StudyLogRepository studyLogRepository;
    private final FrontMatterWriter frontMatterWriter;
    private final EntityManager entityManager;

    public MarkdownExportService(StudyLogRepository studyLogRepository,
                                 FrontMatterWriter frontMatterWriter,
                                 EntityManager entityManager) {
        this.studyLogRepository = studyLogRepository;
        this.frontMatterWriter = frontMatterWriter;
        this.entityManager = entityManager;
    }

    /**
     * {@code open-in-view=false} 라 지연 로딩된 태그를 읽으려면 쓰기 전체가 한 트랜잭션 안에
     * 있어야 한다. 응답을 흘려보내는 동안 읽기 전용 트랜잭션이 열려 있는데, 사용자가 하나뿐인
     * 도구라 감수한다.
     *
     * <p>덩어리마다 영속성 컨텍스트를 비우는 것이 나눠 읽는 유일한 이유다 — 비우지 않으면
     * 읽은 기록이 1차 캐시에 그대로 쌓여 전량을 한 번에 부르는 것과 메모리가 같다.
     */
    @Transactional(readOnly = true)
    public void writeZip(OutputStream out) throws IOException {
        ExportFileNameResolver names = new ExportFileNameResolver();
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);

        long afterId = 0L;
        List<StudyLog> chunk;
        while (!(chunk = studyLogRepository.findChunkAfterId(afterId, Limit.of(CHUNK_SIZE)))
                .isEmpty()) {
            for (StudyLog log : chunk) {
                writeEntry(zip, names.resolve(log), log);
            }
            afterId = chunk.getLast().getId();
            entityManager.clear();
        }

        // try-with-resources 를 쓰지 않는 것이 여기의 요점이다. close() 는 중앙 디렉터리를 쓰므로,
        // 실패 경로에서도 닫으면 도중에 끊긴 내보내기가 "정상으로 열리는데 일부만 든 ZIP" 이 된다 —
        // 응답은 이미 나가 버린 뒤라 그 사실을 알릴 자리도 없다. 끊긴 바이트는 압축 도구가 즉시 거른다
        zip.close();
    }

    private void writeEntry(ZipOutputStream zip, String name, StudyLog log) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // 압축을 푼 파일의 수정 시각을 기록의 수정 시각으로 — 없으면 전부 내려받은 순간으로 뭉개진다.
        // ZIP 이 담는 것이 시간대 없는 지역 시각이라 setTime 대신 setTimeLocal 로 그대로 넘긴다:
        // 시간대를 끼우면 updatedAt 을 찍은 시간대와 어긋나는 순간 UTC 배포 호스트에서 9시간 밀린다
        entry.setTimeLocal(log.getUpdatedAt());

        zip.putNextEntry(entry);
        zip.write(frontMatterWriter.write(log).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
