package com.minky.studylog.service.importer;

import com.minky.studylog.domain.StudyLog;
import com.minky.studylog.repository.StudyLogRepository;
import com.minky.studylog.service.CategoryService;
import com.minky.studylog.service.TagNormalizer;
import com.minky.studylog.service.importer.ImportReport.Failure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * "성공 99" 가 뜨는데 DB 에는 아무것도 없다.
 */
@Service
public class MarkdownImportService {

    private static final String MARKDOWN_SUFFIX = ".md";
    private static final String ZIP_SUFFIX = ".zip";

    /** 맥이 ZIP 에 끼워 넣는 메타데이터 폴더. 안의 파일도 `.md` 로 끝나 확장자로는 갈리지 않는다. */
    private static final String MAC_METADATA_PREFIX = "__MACOSX/";

    private static final int MAX_ENTRIES = 2_000;
    private static final int MAX_ENTRY_BYTES = 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    private final FrontMatterParser frontMatterParser;
    private final StudyLogRepository studyLogRepository;
    private final CategoryService categoryService;

    public MarkdownImportService(FrontMatterParser frontMatterParser,
                                 StudyLogRepository studyLogRepository,
                                 CategoryService categoryService) {
        this.frontMatterParser = frontMatterParser;
        this.studyLogRepository = studyLogRepository;
        this.categoryService = categoryService;
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
                batch.accept(name(file), read(file));
            }
        }
        return batch.report();
    }

    private void readZip(MultipartFile file, Batch batch) {
        String zipName = name(file);
        long totalBytes = 0;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(),
                StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (!isMarkdown(entry)) {
                    continue;
                }
                String entryName = zipName + "/" + entry.getName();
                if (++entries > MAX_ENTRIES || totalBytes > MAX_TOTAL_BYTES) {
                    batch.fail(entryName, "압축 파일이 상한을 넘어 나머지를 읽지 않음");
                    return;
                }
                byte[] bytes = readCapped(zip);
                if (bytes == null) {
                    batch.fail(entryName, "파일 하나가 1MB 를 넘음");
                    continue;
                }
                totalBytes += bytes.length;
                batch.accept(entryName, new String(bytes, StandardCharsets.UTF_8));
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

    /** 상한을 넘는 순간 멈춘다 — 다 읽고 나서 재면 그 시점에 이미 힙에 올라와 있다. */
    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = in.read(buffer)) != -1; ) {
            if (out.size() + read > MAX_ENTRY_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
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

    /**
     * 한 번의 가져오기가 쥐는 상태. 같은 배치 안의 중복은 DB 로는 갈리지 않는다 — 앞 건이
     * 커밋되기 전에 뒤 건이 존재 여부를 물으므로, 본 것을 여기서 따로 센다.
     */
    private final class Batch {

        private final Set<String> seen = new HashSet<>();
        private final List<Failure> failures = new ArrayList<>();
        private int succeeded;
        private int skipped;

        private void accept(String fileName, String content) {
            try {
                save(frontMatterParser.parse(content));
            } catch (ImportFormatException e) {
                failures.add(new Failure(fileName, e.getMessage()));
            } catch (RuntimeException e) {
                // 파서를 통과한 값이 저장에서 막히는 경우 — 파일 하나의 문제라 배치를 세우지 않는다
                failures.add(new Failure(fileName, "저장 실패: " + e.getMessage()));
            }
        }

        private void save(ParsedNote note) {
            if (!seen.add(key(note)) || studyLogRepository
                    .existsByStudyDateAndTitleIgnoreCase(note.date(), note.title())) {
                skipped++;
                return;
            }
            studyLogRepository.save(new StudyLog(
                    note.title(),
                    note.date(),
                    note.start(),
                    note.end(),
                    categoryService.resolve(note.category()),
                    normalized(note.tags()),
                    note.summary(),
                    note.body()));
            succeeded++;
        }

        private void fail(String fileName, String reason) {
            failures.add(new Failure(fileName, reason));
        }

        private ImportReport report() {
            return new ImportReport(succeeded, skipped, List.copyOf(failures));
        }
    }

    private static String key(ParsedNote note) {
        return note.date() + "\u0000" + note.title().toLowerCase(Locale.ROOT);
    }

    /** 태그는 저장 형태가 소문자라 화면 입력과 같은 규칙을 태운다 — 여기만 원문을 남기면 갈라진다. */
    private static Set<String> normalized(Set<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalized = TagNormalizer.normalize(tag);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }
}
