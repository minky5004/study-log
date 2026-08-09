package com.minky.studylog.service.importer;

import com.minky.studylog.service.importer.ImportReport.Failure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 올린 마크다운·ZIP 을 기록으로 넣는다.
 * <p>
 * <b>배치 전체를 한 트랜잭션으로 묶지 않는다.</b> 남의 파일을 받는 경로라 실패가 정상 흐름의
 * 일부이고, 묶으면 백 건 중 한 건의 형식 오류가 나머지 아흔아홉을 되돌린다 — 그 결과 화면에는
 * "성공 99" 가 뜨는데 DB 에는 아무것도 없다. 트랜잭션 경계는 노트 하나이고
 * {@link NoteWriter} 가 소유한다.
 * <p>
 * 그러므로 <b>파일 하나를 다루는 모든 단계가 try 안에 있어야 한다.</b> 읽기 하나가 밖으로
 * 새면 그 파일에서 배치가 끝나고, 이미 커밋된 앞 건들은 보고 없이 사라진 것처럼 보인다.
 */
@Service
public class MarkdownImportService {

    private static final String MARKDOWN_SUFFIX = ".md";
    private static final String ZIP_SUFFIX = ".zip";

    /** 맥이 ZIP 에 끼워 넣는 메타데이터 폴더. 안의 파일도 `.md` 로 끝나 확장자로는 갈리지 않는다. */
    private static final String MAC_METADATA_PREFIX = "__MACOSX/";

    private static final int MAX_ENTRIES = 2_000;
    private static final int MAX_ENTRY_BYTES = 1024 * 1024;

    /**
     * <b>압축을 푼 뒤</b> 크기의 상한. 담은 것만 세면 상한이 아니다 — 건너뛴 엔트리와 1MB 에서
     * 거부한 엔트리도 이미 풀린 뒤이고, 그것들을 빼고 세면 몇 KB 짜리 ZIP 하나로 수 GB 를
     * 풀게 만들 수 있다.
     */
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;

    private final FrontMatterParser frontMatterParser;
    private final NoteWriter noteWriter;

    public MarkdownImportService(FrontMatterParser frontMatterParser, NoteWriter noteWriter) {
        this.frontMatterParser = frontMatterParser;
        this.noteWriter = noteWriter;
    }

    public ImportReport importFrom(List<MultipartFile> files) {
        Batch batch = new Batch();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (name(file).toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX)) {
                readZip(file, batch);
            } else {
                batch.accept(name(file), () -> read(file));
            }
        }
        return batch.report();
    }

    private void readZip(MultipartFile file, Batch batch) {
        String zipName = name(file);
        long inflated = 0;

        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(),
                StandardCharsets.UTF_8)) {
            for (int entries = 0; ; ) {
                ZipEntry entry = zip.getNextEntry();
                if (entry == null) {
                    return;
                }
                if (++entries > MAX_ENTRIES) {
                    batch.fail(zipName, "압축 안 파일이 " + MAX_ENTRIES + "개를 넘어 나머지를 읽지 않음");
                    return;
                }

                long budget = MAX_TOTAL_BYTES - inflated;
                if (!isMarkdown(entry)) {
                    // 건너뛸 엔트리도 풀리는 것은 같다. 세지 않으면 상한 밖에 남는다
                    inflated += drain(zip, budget);
                } else {
                    Chunk chunk = readCapped(zip, budget);
                    inflated += chunk.consumed();
                    String entryName = zipName + "/" + entry.getName();
                    if (chunk.content() == null) {
                        batch.fail(entryName, "파일 하나가 1MB 를 넘음");
                    } else {
                        batch.accept(entryName,
                                () -> new String(chunk.content(), StandardCharsets.UTF_8));
                    }
                }

                if (inflated >= MAX_TOTAL_BYTES) {
                    batch.fail(zipName, "압축을 푼 크기가 20MB 를 넘어 나머지를 읽지 않음");
                    return;
                }
            }
        } catch (IOException e) {
            batch.fail(zipName, "압축을 읽지 못함: " + e.getMessage());
        }
    }

    private static boolean isMarkdown(ZipEntry entry) {
        String name = entry.getName();
        return !entry.isDirectory()
                && !name.startsWith(MAC_METADATA_PREFIX)
                && name.toLowerCase(Locale.ROOT).endsWith(MARKDOWN_SUFFIX);
    }

    /** 읽은 것과 <b>푼 것</b>을 함께 돌려준다. 거부한 엔트리도 그 지점까지는 이미 풀렸다. */
    private record Chunk(byte[] content, long consumed) {
    }

    /** 상한을 넘는 순간 멈춘다 — 다 읽고 나서 재면 그 시점에 이미 힙에 올라와 있다. */
    private static Chunk readCapped(InputStream in, long budget) throws IOException {
        long cap = Math.min(MAX_ENTRY_BYTES, budget);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = in.read(buffer)) != -1; ) {
            if (out.size() + read > cap) {
                return new Chunk(null, out.size() + read);
            }
            out.write(buffer, 0, read);
        }
        return new Chunk(out.toByteArray(), out.size());
    }

    /** 남은 예산까지만 흘려보낸다. 예산을 다 쓰면 호출자가 압축 전체를 접는다. */
    private static long drain(InputStream in, long budget) throws IOException {
        long total = 0;
        for (long skipped; total < budget && (skipped = in.skip(budget - total)) > 0; ) {
            total += skipped;
        }
        return total;
    }

    private static String read(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ImportFormatException("파일을 읽지 못함: " + e.getMessage());
        }
    }

    private static String name(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "이름 없는 파일" : original;
    }

    /** 내용을 꺼내는 일 자체가 실패할 수 있어 값이 아니라 부름을 넘긴다. */
    @FunctionalInterface
    private interface Content {

        String get();
    }

    /**
     * 한 번의 가져오기가 쥐는 상태. 같은 배치 안의 중복은 DB 로는 갈리지 않는다 — 앞 건이
     * 커밋되기 전에 뒤 건이 존재 여부를 물으므로, 본 것을 여기서 따로 센다.
     */
    private final class Batch {

        private final Set<String> seen = new HashSet<>();
        private final List<Failure> failures = new ArrayList<>();
        private int succeeded;
        private int skipped;

        private void accept(String fileName, Content content) {
            try {
                save(frontMatterParser.parse(content.get()));
            } catch (ImportFormatException e) {
                failures.add(new Failure(fileName, e.getMessage()));
            } catch (RuntimeException e) {
                // 파서를 통과한 값이 저장에서 막히는 경우 — 파일 하나의 문제라 배치를 세우지 않는다
                failures.add(new Failure(fileName, "저장 실패: " + e.getMessage()));
            }
        }

        private void save(ParsedNote note) {
            if (!seen.add(key(note)) || !noteWriter.write(note)) {
                skipped++;
                return;
            }
            succeeded++;
        }

        private void fail(String fileName, String reason) {
            failures.add(new Failure(fileName, reason));
        }

        private ImportReport report() {
            return new ImportReport(succeeded, skipped, List.copyOf(failures));
        }
    }

    /** DB 판정과 같은 키여야 한다 — 어긋나면 파일 사이 중복과 DB 중복의 기준이 갈린다. */
    private static String key(ParsedNote note) {
        return note.date() + "\0" + note.title().toLowerCase(Locale.ROOT)
                + "\0" + note.start() + "\0" + note.end();
    }
}
