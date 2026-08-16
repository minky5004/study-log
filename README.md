# study-log

> 공부 시간의 세션 단위 기록 · 쌓인 기록에 검색·통계로 되묻기 — 둘을 겸하는 웹 애플리케이션

**[study-log-n6ez.onrender.com](https://study-log-n6ez.onrender.com)** — 무료 티어 · 첫 접속은
기동 대기 약 3분 · 뜬 뒤로는 1초 안팎

![CI](https://github.com/minky5004/study-log/actions/workflows/ci.yml/badge.svg)

마크다운 파일로 학습 노트를 쌓던 TIL 리포의 대체. 파일의 한계는 둘 — 분야·기간·태그 조합으로
좁히기 불가 · 파일 목록만으로 꾸준함 확인 불가. DB 이관으로 검색·통계 확보. 그 대가로 잃을 뻔한
파일 형식은 **마크다운 재내보내기로 회수** — 옵시디언 vault 에서 그대로 열람.

![홈에서 검색, 상세, 통계까지](docs/screenshots/demo.gif)

![통계 — 일별 잔디 · 주간 추이 · 분야별 · 시간대](docs/screenshots/stats.png)

화면의 130개 세션은 더미 아닌 실제 커밋 이력 — 개인 리포 8개의 커밋을 90분 간격으로 끊어 세션으로
묶고 마크다운 변환 후 `/import` 업로드. 이관 전용 도구 미제작 — 가져오기가 그 통로.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 · Spring Data JPA · Spring Security · Validation |
| Database | PostgreSQL (운영 Neon · 컨테이너 · 리포지토리 테스트) · H2 (로컬 · 나머지 테스트) · 스키마는 Flyway |
| View | Thymeleaf 서버 렌더링(별도 프론트엔드 빌드 없음) · Chart.js · 히트맵은 CSS Grid 자체 구현 |
| Markdown | commonmark-java 렌더링 + jsoup 새니타이즈 |
| Build · CI | Gradle · GitHub Actions · Docker Compose |

## 실행

```bash
git clone https://github.com/minky5004/study-log.git && cd study-log
docker run --rm httpd:alpine htpasswd -bnBC 10 "" 원하는비밀번호 | tr -d ':\n'
cp .env.example .env
# 출력된 BCrypt 해시를 .env 의 APP_ADMIN_PASSWORD_HASH 에 작은따옴표째 붙여넣기
docker compose up -d        # → http://localhost:8080
```

컨테이너 없이 로컬 H2 로 띄우는 경로 — 두 환경변수 설정 후 `./gradlew bootRun`.

```bash
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD_HASH='$2y$10$...'
./gradlew bootRun           # → http://localhost:8080
```

관리자 계정은 환경변수 전용 — 코드에 둔 기본 계정은 곧 리포에 공개된 자격증명. 두 변수 누락 ·
BCrypt 해시 아닌 평문 모두 부팅 실패.

**해시의 작은따옴표 유지.** 벗겼을 때 물리는 자리 — 컴포즈가 안의 `$` 를 변수로 해석 · 값 절단 ·
잘린 값도 `$2` 로 시작해 검사 통과 · 앱은 멀쩡히 기동 · 로그인만 영영 실패. `.env.example`
자리표시자를 그대로 둔 경우도 동일.

조회는 로그인 없이 공개. 작성·수정·삭제와 **내보내기·가져오기**는 로그인 필요. 뒤 둘이 GET 인데도
인증을 거는 이유 — 한 요청이 DB 전량을 흘려보내거나 받는 쪽 · 화면 단위 조회와 다른 취급.

## 설계 판단

| 정한 것 | 왜 그렇게 했나 |
|---|---|
| 공부 시간을 저장 시점에 계산해 컬럼으로 | 통계를 열 때마다 기록 전량 재계산 회피. 종료가 시작보다 앞서는 값은 익일 처리. 시작·종료가 같은 값은 입력 거부 — 0분과 24시간 구별 불가 |
| 분야는 최초 표기 유지 · 태그는 소문자 통일 | 분야는 화면에 이름 그대로 노출 · 태그는 소문자로 변형 노출. 대소문자만 다른 분야가 통계를 가르지 않게 중복 판정은 정규화 컬럼 `name_key` 의 unique 제약 — `lower(name)` 함수 인덱스로 대신하지 않는 이유는 키 파생이 소문자화에 더해 공백 축약까지 겸하는 것. 붙여넣은 전각 공백이 든 분야까지 같은 키로 접는 이유 — 키가 갈릴 때의 통계 분열 · 합칠 관리 화면 부재 |
| 주·월·시간대로 접는 일만 DB 가 아니라 자바에서 | 날짜 함수는 H2 와 PostgreSQL 이 갈리는 자리 — 방언에 닿는 계산만 걷어내 순수 단위 테스트로 덮기. 합계 자체는 `group by` 로 DB 가 접는 몫 — 그것까지 자바로 가져올 때의 대가는 매 요청 기록 수만큼의 행 전송 |
| 스키마는 Flyway 가 만들고 하이버네이트는 대조만 | `ddl-auto=update` 에서 실제로 물린 자리 — 태그 순서 컬럼 추가 DDL 실패 · 경고 한 줄만 남기고 부팅 성공 · 목록 500 인 채로 기동 유지. `validate` 는 같은 상황에서 기동 자체를 차단. **그 검증에 반대로 물린 자리** — 적용이 끝난 V1 의 주석 한 줄 수정 · checksum 변경 · 08-16 운영 부팅 정지 · 로컬 볼륨의 같은 V1 은 `BASELINE` 타입 · checksum 부재로 끝까지 초록. 그래서 적용된 파일은 불변 · 정오표는 `src/main/resources/db/migration/NOTES.md` |
| 잔디 히트맵은 CSS Grid 자체 구현 | 칸 365개에 Chart.js 플러그인 CDN 추가는 과잉 |
| 가져오기 배치를 한 트랜잭션으로 묶지 않음 | 남의 파일을 받는 경로 — 실패도 정상 흐름의 일부. 묶었을 때의 결과 — 100건 중 1건의 형식 오류에 나머지 99건 롤백 · 화면에는 "성공 99" · DB 는 빈 상태. 트랜잭션 경계는 노트 하나 |

**감수한 것**

- 검색은 LIKE — `%keyword%` 는 인덱스 미사용. 전환 기준은 기록 5,000건 · 검색 응답 300ms — 그때까지는
  유지. 한국어에 맞는 `pg_bigm` 은 운영 DB(Neon) 미지원 · 가용한 것은 3-gram 인 `pg_trgm` 뿐 —
  두 글자 검색어에 약한 쪽 · 전환 시점에 색인 방식부터 다시 고를 자리
- 리포지토리 테스트는 실제 PostgreSQL 위 — 인메모리 H2 시절 방언 결함 두 건 통과. 검색
  파라미터 `lower(null)` 의 `bytea` 추론 · 기간 필터 파라미터 타입 미결정으로 날짜를 넣은 검색
  전건 500. 대가는 `./gradlew build` 의 도커 의존
- 무료 티어 콜드스타트 166초 — 카드 없는 상시가동 경로 부재가 전제. 그중 앱 부팅은 44.7초 ·
  나머지 121초는 인스턴스 재배치 · DB 웨이크업 몫 — 부팅 단축만으로는 체감 불변
- 공부 계획 체크리스트는 기록과 무연결 — 계획 대비 실제를 통계에 붙일 여지를 버린 자리. 대가는
  "TODO 앱" 으로 읽힐 여지 · 방어는 배치뿐 — 첫 화면 주인공이 아닌 탭 하나 · 홈에서는 최근 기록
  옆 칸 하나

## 구조

```
domain/               엔티티 · 자정 넘김 시간 계산 · 태그 순서 컬럼
repository/           조회 · 통계 group by
service/              CRUD · 분야/태그 정규화 · 통계 집계 · 마크다운 렌더 + 새니타이즈
service/export/       기록 → YAML 프론트매터 마크다운 ZIP
service/importer/     마크다운 ZIP → 기록 (노트 하나가 트랜잭션 하나)
web/                  컨트롤러 · 폼 DTO · 통계 JSON(/api/stats)
resources/templates/  home · logs · stats · plans · io · 공통 layout · 프래그먼트
```

## 검증

| 무엇을 | 어떻게 |
|---|---|
| 단위·통합 테스트 256개 | `./gradlew test --rerun-tasks` 24초(3회 실측 24·24·23) · 전부 통과 · 리포지토리 24개는 PostgreSQL 컨테이너 위 — 도커 데몬이 뜬 상태 기준 · 데몬 콜드 기동에서는 첫 회 37초 |
| 백업의 실제 복구 여부 | 내보낸 ZIP 을 비운 DB 에 되넣어 원본과 일치 · 같은 ZIP 재업로드는 전건 건너뛰기 (`MarkdownRoundTripTest`) |
| PostgreSQL 위 이미지 기동 여부 | CI `image` 잡이 compose 로 띄워 앱 healthcheck 가 healthy 를 낼 때까지 기다린 뒤 `/` `/logs` 200 확인 — 1분 32초 (run 31811431653) |
| 실제 데이터의 화면 반영 여부 | 커밋 이력에서 만든 마크다운 130개를 `/import` 업로드 — 추가 130 · 건너뜀 0 · 실패 0 |
| 배포본의 기동·응답 | 무접속 18분 뒤 첫 요청 166초 · 깨어난 직후 재요청 1.76초 · 데워진 뒤 0.13~1.18초 |
| 배포 스키마의 적용 | 빈 DB 에 V1(08-13) · 기록 130건 위에 V2(08-16) · 이력표 둘 다 `success = t` — 130건 온전 · `/` `/logs` `/plans` `/stats` 200 · `/api/stats` 의 `heatmap` `trend` `categories` `hours` 넷 전부 200 |
