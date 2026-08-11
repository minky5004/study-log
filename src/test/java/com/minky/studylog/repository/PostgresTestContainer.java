package com.minky.studylog.repository;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 리포지토리 테스트가 붙는 실제 PostgreSQL.
 *
 * <p>테스트 전반은 인메모리 H2 로 남긴다 — 컨테이너를 전부에 깔면 몇 초짜리 서비스·화면
 * 테스트까지 도커에 인질로 잡힌다. 반대로 <b>방언 계약을 지는 곳은 리포지토리</b>라, 거기만
 * 운영과 같은 엔진 위로 옮긴다. {@code MODE=PostgreSQL} 이 방언을 재현하지 못한다는 것은
 * 검색 쿼리의 {@code lower(null)} 이 {@code bytea} 로 추론돼 목록이 통째로 죽은 일로 이미 겪었고,
 * 그때 H2 는 끝까지 초록이었다.
 *
 * <p>{@code @Container} 정적 필드가 아니라 빈으로 두는 것은 컨테이너 수명을 스프링 컨텍스트에
 * 맡기기 위해서다. <b>컨텍스트 설정이 같은 테스트끼리만</b> 캐시를 타므로 컨테이너도 그 범위에서
 * 하나다 — 슬라이스나 프로퍼티가 다르면 컨텍스트가 갈라지고 컨테이너도 따로 뜬다. 클래스마다
 * 정적 필드를 두는 형태는 그 재사용마저 잃는다.
 *
 * <p>쓰는 곳이 같은 패키지 하나뿐이라 패키지 전용으로 둔다. 밖에서 쓸 일이 생기면 그때 옮긴다 —
 * 손이 닿지 않는 자리에 두면 베껴 쓰게 되고, 사본은 곧 두 번째 컨테이너다.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresTestContainer {

    /**
     * 태그를 {@code compose.yaml} 과 맞춘다 — 테스트가 통과한 엔진과 배포되는 엔진이 갈리면
     * 이 그물을 친 이유가 사라진다. 한쪽을 올리는 날은 다른 쪽도 같이 올린다.
     */
    private static final String IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
