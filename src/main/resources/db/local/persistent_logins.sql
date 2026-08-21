-- 로컬 H2 전용. 정본은 db/migration/V3__persistent_logins.sql 이고 이것은 그 사본이다.
--
-- 사본이 생기는 이유는 로컬이 Flyway 를 끄고 돌기 때문이다(application-local.properties 의 근거
-- 참고). 스키마를 만드는 것은 하이버네이트인데 이 테이블에는 엔티티가 없어 ddl-auto=update 가
-- 그냥 지나치고, 그러면 체크박스를 켠 로그인이 없는 테이블에 부딪힌다.
--
-- 두 파일이 갈라지면 로컬에서만 로그인이 깨진다 — 배포본은 V3 를 그대로 쓰고 checksum 가드가
-- 그쪽을 지킨다. V3 를 고치는 날은 이 파일도 같이 본다.

create table if not exists persistent_logins (
    username  varchar(64)  not null,
    series    varchar(64)  primary key,
    token     varchar(64)  not null,
    last_used timestamp(6) not null
);
