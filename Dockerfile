FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 빌드 스크립트만 먼저 넣고 의존성을 받아 둔다 — 소스만 고친 재빌드가 이 레이어를 재사용한다.
# 여기서 실패해도 넘어가는 것은 의존성 캐싱이 목적이지 검증이 목적이 아니기 때문
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

# LocalDateTime.now() 가 읽는 것은 JVM 기본 시간대다. 컨테이너 기본값은 UTC 라
# 그대로 두면 createdAt 만 아홉 시간 뒤로 어긋난다 — 화면의 날짜는 Asia/Seoul 로 고정돼 있다
ENV TZ=Asia/Seoul

# application.properties 가 local 을 바닥값으로 깔고 있어, 이것이 없으면 변수를 빠뜨린 실행이
# 조용히 파일 H2 로 뜨고 h2 콘솔까지 열린다. 컨테이너를 띄운 사람은 돌아간다고 믿지만
# 기록은 컨테이너와 함께 사라진다. prod 를 박아 두면 접속정보 없이는 부팅이 실패한다
ENV SPRING_PROFILES_ACTIVE=prod

COPY --from=build /app/build/libs/*.jar app.jar

# 앱은 어디에도 쓰지 않는다 — 저장은 전부 DB 다. root 로 돌 이유가 없다
RUN useradd --system --uid 10001 --create-home appuser
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
