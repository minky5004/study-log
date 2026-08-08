package com.minky.studylog.service.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.minky.studylog.domain.Category;
import com.minky.studylog.domain.StudyLog;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파일명은 압축을 푼 사람이 보는 첫 화면이자 ZIP 엔트리 키다. 중복이 하나라도 나면
 * {@code ZipOutputStream} 이 그 자리에서 던지므로, 이름을 정하는 쪽이 이미 쓴 이름도 함께 쥔다.
 */
class ExportFileNameResolverTest {

    @Test
    @DisplayName("파일명은 날짜-제목.md")
    void basicName() {
        assertThat(resolve("트랜잭션 격리 수준", "2026-08-03", "23:00"))
                .isEqualTo("2026-08-03-트랜잭션 격리 수준.md");
    }

    @Test
    @DisplayName("파일명 금지문자 치환")
    void replacesIllegalCharacters() {
        assertThat(resolve("A/B\\C:D*E?F\"G<H>I|J", "2026-08-03", "23:00"))
                .isEqualTo("2026-08-03-A_B_C_D_E_F_G_H_I_J.md");
    }

    @Test
    @DisplayName("같은 날 같은 제목이면 시각 HHmm 을 덧붙여 구분")
    void appendsTimeOnCollision() {
        ExportFileNameResolver resolver = new ExportFileNameResolver();
        resolver.resolve(log("JPA", "2026-08-03", "09:00"));

        assertThat(resolver.resolve(log("JPA", "2026-08-03", "23:05")))
                .isEqualTo("2026-08-03-JPA-2305.md");
    }

    @Test
    @DisplayName("시각까지 같으면 순번을 덧붙임")
    void appendsSequenceOnFullCollision() {
        ExportFileNameResolver resolver = new ExportFileNameResolver();
        resolver.resolve(log("JPA", "2026-08-03", "09:00"));
        resolver.resolve(log("JPA", "2026-08-03", "23:05"));

        assertThat(resolver.resolve(log("JPA", "2026-08-03", "23:05")))
                .isEqualTo("2026-08-03-JPA-2305-2.md");
    }

    /**
     * 같은 이름을 세 번 물어도 매번 다른 답이 나와야 한다 — 이름 등록을 호출자에게 맡기면
     * 잊는 순간 압축 도중에, 그것도 특정 데이터에서만 터진다.
     */
    @Test
    @DisplayName("같은 이름이 이어져도 매번 다른 이름")
    void neverRepeatsAName() {
        ExportFileNameResolver resolver = new ExportFileNameResolver();

        assertThat(Set.of(
                resolver.resolve(log("JPA", "2026-08-03", "23:05")),
                resolver.resolve(log("JPA", "2026-08-03", "23:05")),
                resolver.resolve(log("JPA", "2026-08-03", "23:05")))).hasSize(3);
    }

    @Test
    @DisplayName("제목 끝 공백 · 마침표 제거 — 윈도우에서 열리지 않는 이름 방지")
    void trimsTrailingDotAndSpace() {
        assertThat(resolve("정리...  ", "2026-08-03", "09:00")).isEqualTo("2026-08-03-정리.md");
    }

    /**
     * 글자 수가 아니라 <b>바이트</b>로 잰다. ext4·APFS 의 한계가 255바이트라, 한글 제목을 글자
     * 수로만 자르면 이름이 세 배로 불어 압축을 풀 때 그 기록만 조용히 빠진다.
     */
    @Test
    @DisplayName("아주 긴 제목은 UTF-8 바이트 예산에 맞춰 잘라냄")
    void truncatesLongTitle() {
        String name = resolve("가".repeat(300), "2026-08-03", "09:00");

        assertThat(name.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(200);
        assertThat(name).startsWith("2026-08-03-가").endsWith(".md");
    }

    /** 순번이 길어지면 제목 몫이 줄어야 한다 — 접미사 자리를 상수로 떼어 두면 그 가정이 깨진다. */
    @Test
    @DisplayName("충돌 접미사가 붙어도 바이트 예산 유지")
    void keepsBudgetWithCollisionSuffix() {
        ExportFileNameResolver resolver = new ExportFileNameResolver();

        for (int i = 0; i < 12; i++) {
            assertThat(resolver.resolve(log("가".repeat(300), "2026-08-03", "23:05"))
                    .getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(200);
        }
    }

    /**
     * 자르는 자리가 UTF-16 단위면 이모지 한 자가 짝 잃은 서러게이트로 남고, ZIP 엔트리 이름에
     * {@code ?} 로 박힌다.
     */
    @Test
    @DisplayName("잘린 자리에 짝 잃은 서러게이트를 남기지 않음")
    void neverSplitsSurrogatePairs() {
        String name = resolve("가".repeat(60) + "😀".repeat(20), "2026-08-03", "09:00");

        // UTF-8 왕복이 이름을 바꾸면 그 자리에 짝 잃은 서러게이트가 있었다는 뜻 — 인코더가 ? 로 바꾼다
        assertThat(new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .isEqualTo(name);
        assertThat(name).doesNotContain("?");
    }

    /**
     * 치환·절단이 제목을 통째로 지워도 이름은 남아야 한다. 확장자만 남은 파일은 도구에 따라
     * 숨김 파일로 잡혀 압축을 푼 사람 눈에서 사라진다.
     */
    @Test
    @DisplayName("금지문자뿐인 제목에도 날짜가 이름으로 남음")
    void keepsDateWhenTitleVanishes() {
        assertThat(resolve("///", "2026-08-03", "09:00")).isEqualTo("2026-08-03-___.md");
        assertThat(resolve("...", "2026-08-03", "09:00")).isEqualTo("2026-08-03.md");
    }

    private static String resolve(String title, String date, String start) {
        return new ExportFileNameResolver().resolve(log(title, date, start));
    }

    private static StudyLog log(String title, String date, String start) {
        return new StudyLog(title, LocalDate.parse(date), LocalTime.parse(start),
                LocalTime.parse(start).plusHours(1), new Category("Spring"), Set.of(), null, null);
    }
}
