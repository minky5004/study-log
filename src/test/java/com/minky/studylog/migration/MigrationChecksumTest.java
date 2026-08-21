package com.minky.studylog.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마이그레이션 파일이 손대지 않은 채로 있는지 지키는 가드.
 *
 * <p>2026-08-16 에 V1 의 헤더 주석 한 줄이 늘면서 checksum 이 {@code 1981831069} 에서
 * {@code 178111068} 로 옮겨갔고, 그 값을 이미 저장해 둔 운영 Neon 에서 부팅 {@code validate} 가
 * 불일치를 잡아 배포가 죽었다. 로컬 볼륨의 V1 은 {@code BASELINE} 타입이라 checksum 이 없고
 * 테스트는 매번 빈 DB 에서 시작하며 H2 는 Flyway 를 끄므로, 그때 이 사고를 잡은 자동 경로는
 * 하나도 없었다.
 *
 * <p>기댓값의 정본은 {@code src/test/resources/db/migration-checksums.txt} 다. 이 테스트가
 * 지키는 것은 "목록에 오른 파일의 checksum 이 그 값 그대로" 이며, 배포 여부는 알지 못한다.
 * 그래서 불일치 메시지는 배포된 파일과 배포 전 파일 두 갈래를 함께 말한다.
 *
 * <p>V1 의 기댓값만은 운영 Neon 의 {@code flyway_schema_history} 가 실제로 가진 값이라, 그 한
 * 줄에 한해 로컬 초록이 운영과의 일치를 뜻한다. 나머지 줄은 작업 트리에서 계산한 값이다.
 *
 * <p>Flyway 의 계산기를 그대로 쓰는 것은 자체 해시로는 운영과 같은 단위를 얻을 수 없기
 * 때문이다. {@link ChecksumCalculator} 는 internal 이지만 Flyway 를 올릴 때 사라지면 컴파일이
 * 깨져 조용히 넘어가지 않는다.
 */
class MigrationChecksumTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final String EXPECTED_RESOURCE = "/db/migration-checksums.txt";
    private static final String EXPECTED_PATH = "src/test/resources" + EXPECTED_RESOURCE;
    private static final String NOTES_PATH = "src/main/resources/db/migration/NOTES.md";

    private static Map<String, Integer> expected;

    @BeforeAll
    static void loadExpectedChecksums() {
        expected = readExpectedChecksums();
    }

    @Test
    @DisplayName("목록에 오른 마이그레이션이 그 이름으로 그대로 있다")
    void recordedMigrationsStillExist() {
        Set<String> present = new LinkedHashSet<>();
        migrationFiles().forEach(file -> present.add(file.getFileName().toString()));

        List<String> violations = new ArrayList<>();
        expected.keySet().forEach(name -> {
            if (!present.contains(name)) {
                violations.add(name + " 이 목록에 있는데 파일이 없습니다."
                        + " 이미 배포된 마이그레이션이면 이름을 되돌립니다 — " + NOTES_PATH + " 참고."
                        + " 아직 배포 전이면 " + EXPECTED_PATH + " 의 줄을 새 이름으로 고칩니다.");
            }
        });
        failIfAny(violations);
    }

    @Test
    @DisplayName("목록에 오른 마이그레이션의 checksum 이 기댓값 그대로다")
    void recordedMigrationsKeepTheirChecksum() {
        Map<String, Path> byName = new LinkedHashMap<>();
        migrationFiles().forEach(file -> byName.put(file.getFileName().toString(), file));

        List<String> violations = new ArrayList<>();
        expected.forEach((name, want) -> {
            Path file = byName.get(name);
            if (file == null) {
                return; // 부재는 위 테스트가 그쪽 이야기로 보고한다
            }
            int actual = checksumOf(file);
            if (actual != want) {
                violations.add(name + " 의 checksum 이 " + actual + " 로 바뀌었습니다(기대 " + want + ")."
                        + " 이미 배포된 마이그레이션이면 파일을 되돌립니다 — " + NOTES_PATH + " 참고."
                        + " 아직 배포 전이면 " + EXPECTED_PATH + " 의 값을 " + actual + " 로 고칩니다.");
            }
        });
        failIfAny(violations);
    }

    @Test
    @DisplayName("새 마이그레이션은 기댓값 목록에 올라와 있어야 한다")
    void everyMigrationIsRecorded() {
        List<String> violations = new ArrayList<>();
        migrationFiles().forEach(file -> {
            String name = file.getFileName().toString();
            if (!expected.containsKey(name)) {
                violations.add(name + " 이 기댓값 목록에 없어 가드 밖에 있습니다. " + EXPECTED_PATH
                        + " 에 다음 줄을 추가하세요: " + name + " " + checksumOf(file));
            }
        });
        failIfAny(violations);
    }

    /**
     * 버전 마이그레이션만 본다. 반복 실행되는 {@code R__} 은 checksum 이 바뀌면 다시 도는 것이
     * 정상이라 애초에 이 가드의 대상이 아니다.
     *
     * <p>하위 디렉터리까지 걷는 것은 Flyway 가 {@code classpath:db/migration} 을 재귀로 훑기
     * 때문이다. 한 겹 아래에 둔 파일은 운영에서 적용되면서 목록에는 안 잡히는, 이 가드가 가장
     * 조용히 무너지는 형태가 된다. 같은 이유로 파일명이 겹치면 실패시킨다 — 목록의 키가
     * 파일명이라 두 파일 중 하나만 지켜지는 것을 알아챌 방법이 없다.
     */
    private static List<Path> migrationFiles() {
        assertThat(Files.isDirectory(MIGRATION_DIR))
                .as("마이그레이션 디렉터리 %s 를 찾지 못했습니다 — 프로젝트 루트에서 실행되는지 "
                        + "확인하세요. 경로가 어긋난 채 0건을 검사하고 통과하는 것이 이 가드에서 "
                        + "가장 나쁜 실패입니다.", MIGRATION_DIR.toAbsolutePath())
                .isTrue();

        List<Path> files;
        try (Stream<Path> paths = Files.walk(MIGRATION_DIR)) {
            files = paths.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("V") && name.endsWith(".sql");
            }).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Set<String> names = new LinkedHashSet<>();
        files.forEach(file -> {
            String name = file.getFileName().toString();
            assertThat(names.add(name))
                    .as("마이그레이션 파일명 %s 가 %s 아래에 둘 이상 있습니다 — 기댓값 목록은 "
                            + "파일명으로 식별하므로 하나만 지켜집니다.", name, MIGRATION_DIR)
                    .isTrue();
        });
        return files;
    }

    private static Map<String, Integer> readExpectedChecksums() {
        Map<String, Integer> checksums = new LinkedHashMap<>();
        try (InputStream in = MigrationChecksumTest.class.getResourceAsStream(EXPECTED_RESOURCE)) {
            assertThat(in).as("기댓값 목록 %s 를 클래스패스에서 찾지 못했습니다.", EXPECTED_PATH)
                    .isNotNull();
            List<String> lines =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                assertThat(parts).as("%s 의 %d 번째 줄이 `파일명 checksum` 형식이 아닙니다: %s",
                        EXPECTED_PATH, i + 1, line).hasSize(2);
                Integer previous = checksums.put(parts[0], Integer.parseInt(parts[1]));
                assertThat(previous).as("%s 의 %d 번째 줄이 %s 를 다시 적습니다 — 뒤엣값이 앞엣값을 "
                        + "조용히 덮으면 정본이 어느 값인지 알 수 없습니다.",
                        EXPECTED_PATH, i + 1, parts[0]).isNull();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(checksums).as("기댓값 목록 %s 가 비어 있습니다.", EXPECTED_PATH).isNotEmpty();
        return checksums;
    }

    private static int checksumOf(Path file) {
        return ChecksumCalculator.calculate(new MigrationFile(file));
    }

    private static void failIfAny(List<String> violations) {
        if (!violations.isEmpty()) {
            fail(String.join(System.lineSeparator(), violations));
        }
    }

    /**
     * Flyway 계산기에 파일을 넘기기 위한 최소 구현. 추상 메서드가 {@code read()} 하나뿐이고
     * 나머지는 경로를 알려주는 것이라 여기서 다 채운다.
     */
    private static final class MigrationFile extends LoadableResource {

        private final Path file;

        private MigrationFile(Path file) {
            this.file = file;
        }

        @Override
        public Reader read() {
            try {
                return Files.newBufferedReader(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getAbsolutePath() {
            return file.toAbsolutePath().toString();
        }

        @Override
        public String getAbsolutePathOnDisk() {
            return file.toAbsolutePath().toString();
        }

        @Override
        public String getFilename() {
            return file.getFileName().toString();
        }

        @Override
        public String getRelativePath() {
            return MIGRATION_DIR.relativize(file).toString();
        }
    }
}
