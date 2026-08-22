# study-log

> 공부 시간의 세션 단위 기록 · 쌓인 기록에 검색·통계로 되묻기 — 둘을 겸하는 웹 애플리케이션

**[study-log-n6ez.onrender.com](https://study-log-n6ez.onrender.com)** — 무료 티어 · 첫 접속은
기동 대기 약 3분 · 뜬 뒤로는 1초 안팎

![CI](https://github.com/minky5004/study-log/actions/workflows/ci.yml/badge.svg)

마크다운 TIL 리포의 대체. 파일의 한계는 둘 — 분야·기간·태그 조합으로 좁히기 불가 · 파일 목록만으로
꾸준함 확인 불가. DB 이관으로 검색·통계 확보 · 잃을 뻔한 파일 형식은 **마크다운 재내보내기로
회수**(옵시디언 vault 에서 그대로 열람).

![홈에서 검색, 상세, 통계까지](docs/screenshots/demo.gif)

![통계 — 일별 잔디 · 주간 추이 · 분야별 · 시간대](docs/screenshots/stats.png)

화면의 130개 세션은 더미 아닌 실제 커밋 이력 — 개인 리포 8개의 커밋을 90분 간격으로 끊어 세션으로
묶고 마크다운 변환 후 `/import` 업로드.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 · Spring Data JPA · Spring Security · Validation |
| Database | PostgreSQL (운영 Neon · 로컬 컨테이너) · H2 (로컬 실행) · 스키마는 Flyway |
| View | Thymeleaf 서버 렌더링(별도 프론트엔드 빌드 없음) · Chart.js · 잔디는 CSS Grid 자체 구현 |
| Markdown | commonmark-java 렌더링 + jsoup 새니타이즈 |
| Test | JUnit 5 · DB 에 닿는 테스트는 Testcontainers 의 실제 PostgreSQL 위 |
| Build · CI | Gradle · GitHub Actions · Docker Compose |

## 실행

```bash
git clone https://github.com/minky5004/study-log.git && cd study-log
docker run --rm httpd:alpine htpasswd -bnBC 10 "" 원하는비밀번호 | tr -d ':\n'
cp .env.example .env
# 출력된 BCrypt 해시를 .env 의 APP_ADMIN_PASSWORD_HASH 에 작은따옴표째 붙여넣기
docker compose up -d        # → http://localhost:8080
```

컨테이너 없이 로컬 H2 로 띄우는 통로.

```bash
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD_HASH='$2y$10$...'
./gradlew bootRun           # → http://localhost:8080
```

관리자 계정은 환경변수 전용 — 두 변수 누락 · BCrypt 해시 아닌 평문 모두 부팅 실패.
**해시의 작은따옴표 유지** — 벗긴 값에 물리는 자리는 컴포즈의 `$` 변수 해석 · 값 절단 · 멀쩡한
기동 · 영영 실패하는 로그인.

조회는 로그인 없이 공개 · 작성·수정·삭제와 **내보내기·가져오기**는 로그인 필요. 뒤 둘이 GET
인데도 인증을 거는 이유 — 한 요청이 DB 전량을 흘려보내거나 받는 쪽 · 화면 단위 조회와 다른 취급.

## 구조

```
study-log/
├── .github/workflows/ci.yml        테스트 · compose 기동 후 PostgreSQL 위 응답 확인
├── compose.yaml · Dockerfile       앱 + PostgreSQL · 프로파일 바닥값 prod
└── src/main/
    ├── java/com/minky/studylog/
    │   ├── config/                 관리자 계정 · 권한 바닥값 · 옛 remember-me 토큰은 로그인 화면으로
    │   ├── domain/                 엔티티 · 자정 넘김 시간 계산 · 태그 순서 컬럼
    │   ├── repository/             조회 · 통계 group by
    │   ├── service/                CRUD · 분야/태그 정규화 · 통계 집계 · 마크다운 렌더 + 새니타이즈
    │   │   ├── export/             기록 → YAML 프론트매터 마크다운 ZIP
    │   │   └── importer/           마크다운 ZIP → 기록 (노트 하나가 트랜잭션 하나)
    │   └── web/                    컨트롤러 · 폼 DTO · 통계 JSON(/api/stats)
    └── resources/
        ├── db/migration/           Flyway V1~V3 · 적용본 불변 · 정오표 NOTES.md
        └── templates/              home · logs · stats · plans · io · 공통 layout · 프래그먼트
```

4계층 단방향 `domain → repository → service → web` · 마크다운 입출력만 `service` 아래 하위 패키지 둘.
