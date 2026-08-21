-- remember-me 영속 토큰. Render 무료가 유휴 15분에 잠들고 깨어날 때 프로세스가 새로 떠서
-- 톰캣 인메모리 세션이 통째로 사라지므로, 로그인을 세션 밖에 남기는 자리가 필요하다.
--
-- 컬럼 이름·타입·길이는 JdbcTokenRepositoryImpl 의 CREATE_TABLE_SQL 을 그대로 옮긴 것이다.
-- 그 클래스가 쓰는 기본 SQL 넷(조회·삽입·갱신·삭제)이 전부 이 이름들을 문자열로 박고 있어,
-- 한 글자라도 바꾸면 SQL 을 넷 다 재정의해야 한다. 원문의 `timestamp` 는 PostgreSQL 에서
-- `timestamp(6)` 과 같은 값이라 리포의 다른 마이그레이션 표기에 맞췄다.
--
-- 엔티티를 만들지 않는다 — 이 테이블은 JPA 가 아니라 JdbcTemplate 이 읽고 쓴다. 그래서
-- 리포지토리 테스트의 `ddl-auto=validate` 는 이 테이블을 보지 않고, 스키마가 어긋나면
-- 부팅이 아니라 로그인이 실패한다.

create table persistent_logins (
    username  varchar(64)  not null,
    series    varchar(64)  primary key,
    token     varchar(64)  not null,
    last_used timestamp(6) not null
);
