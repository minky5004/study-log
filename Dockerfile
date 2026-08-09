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

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
