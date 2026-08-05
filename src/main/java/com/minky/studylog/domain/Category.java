package com.minky.studylog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;

/**
 * 분야는 화면에 이름 그대로 노출되므로 최초 등록 표기를 유지하고, 중복 판정과 조회만
 * 정규화 키로 한다. {@code name} 에 걸린 평범한 unique 제약은 PostgreSQL·H2 모두
 * 대소문자를 구분해 {@code Spring} 과 {@code spring} 을 동시에 허용하므로, 제약을
 * {@code name_key} 로 옮겨 그 경로를 DB 에서 막는다.
 */
@Entity
@Table(name = "category",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_name_key", columnNames = "name_key"))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** {@code name} 에서만 파생되는 조회·중복 판정용 키. 따로 설정하는 경로를 두지 않는다. */
    @Column(name = "name_key", nullable = false, length = 50)
    private String nameKey;

    protected Category() {
    }

    public Category(String name) {
        String display = collapseWhitespace(name);
        if (display.isEmpty()) {
            throw new IllegalArgumentException("분야 이름은 필수");
        }
        this.name = display;
        this.nameKey = display.toLowerCase(Locale.ROOT);
    }

    /**
     * 조회 쪽이 생성자와 같은 규칙으로 키를 만들게 하는 진입점.
     * 소문자 변환에 {@link Locale#ROOT} 를 쓰는 것은 배포 환경 로캘에 따라 같은 분야가
     * 다른 키로 갈라지는 것을 막기 위해서.
     */
    public static String toKey(String rawName) {
        return collapseWhitespace(rawName).toLowerCase(Locale.ROOT);
    }

    /**
     * 공백 축약에 유니코드 인식 {@code (?U)} 를 붙이는 것은 비분리 공백(U+00A0)·전각 공백(U+3000)
     * 때문 — 문서에서 붙여넣은 분야가 타이핑한 것과 다른 키로 갈라지면 통계가 둘로 쪼개지고,
     * 이 프로젝트에는 합칠 분야 관리 화면이 없다. 축약 후에 자르는 순서라 앞뒤도 함께 처리된다.
     */
    private static String collapseWhitespace(String raw) {
        return raw == null ? "" : raw.replaceAll("(?U)\\s+", " ").trim();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }
}
