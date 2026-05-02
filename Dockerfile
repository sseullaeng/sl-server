# =============================================================================
# 쓸랭 백엔드 Dockerfile — multi-stage build
#
# stage 1 (builder) : Gradle 8 + JDK 21 → bootJar
# stage 2 (runtime) : Eclipse Temurin 21 JRE alpine + 비특권 user + Asia/Seoul TZ
#
# 빌드:    docker build -t sseulang-backend:latest .
# 실행 예: docker run --rm --env-file .env.prod -p 8080:8080 sseulang-backend
# =============================================================================

# -------- stage 1: builder --------
FROM gradle:8.14-jdk21-jammy AS builder
WORKDIR /workspace

# 의존성 캐시 — settings/build 만 먼저 복사해 layer 재사용 (소스 변경 시 dependency 재다운로드 X)
COPY settings.gradle build.gradle gradle.properties* ./
COPY gradle ./gradle
COPY gradlew ./
RUN gradle dependencies --no-daemon || true

# 소스 + 빌드
COPY src ./src
RUN gradle bootJar -x test --no-daemon

# -------- stage 2: runtime --------
FROM eclipse-temurin:21-jre-alpine

# Asia/Seoul TZ — LocalDateTime KST 일관성 보장 (FRONTEND_INTEGRATION.md §3 참조).
RUN apk add --no-cache tzdata curl \
 && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
 && echo "Asia/Seoul" > /etc/timezone \
 && apk del tzdata

# 비특권 user — root 로 JVM 실행 X
RUN addgroup -g 1000 sseulang && adduser -u 1000 -G sseulang -s /bin/sh -D sseulang

WORKDIR /app
COPY --from=builder --chown=sseulang:sseulang /workspace/build/libs/*.jar app.jar

USER sseulang

# JVM 옵션 — heap 은 컨테이너 메모리에 맞게 자동 조정. -XX:+UseContainerSupport 는 21 default.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Seoul"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Spring Boot Actuator health 가 살아있을 때만 healthy
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
