# 쓸랭 (Sseulang) 백엔드

중고 거래 + 대여 + 나눔 + 배달대행을 통합한 C2C 플랫폼 백엔드.

> 상세 설계: [docs/backend.md](docs/SSEULANG_BACKEND_GUIDE.md)

## 기술 스택

- Java 21, Spring Boot 3.3.5, Gradle
- JPA(Hibernate) + QueryDSL, Flyway
- MySQL 8 / MongoDB 7 / Redis 7
- Spring Security + JWT (HttpOnly 쿠키), Spring OAuth2 Client (카카오·구글)
- WebSocket + STOMP
- AWS S3 (Presigned URL)
- 토스페이먼츠
- springdoc-openapi (Swagger UI)

## 로컬 실행

### 1) 인프라 띄우기

```bash
docker compose up -d
```

- MySQL: `localhost:3307` (DB `sseulang` / user `sseulang` / pw `sseulangpw`)
- Redis: `localhost:6380`
- MongoDB: `localhost:27017` (user `sseulang` / pw `sseulangpw`)

> 호스트 포트는 다른 로컬 컨테이너와 충돌 방지를 위해 기본 포트가 아닌 값으로 잡혀 있습니다. 컨테이너 내부 포트는 표준 그대로입니다.

### 2) 환경 변수 (선택, 미설정 시 빈 값)

```bash
export JWT_SECRET="local-dev-secret-please-override-with-64-char-random-string-1234567890"
export KAKAO_REST_API_KEY=...
export KAKAO_CLIENT_SECRET=...
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export AWS_ACCESS_KEY=...
export AWS_SECRET_KEY=...
export S3_BUCKET=sseulang-bucket
export TOSS_CLIENT_KEY=...
export TOSS_SECRET_KEY=...
```

### 3) 애플리케이션 실행

```bash
./gradlew bootRun
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 개발 메모

- 브랜치: `main` (배포) ← `dev` (통합) ← `feature/*`
- 커밋: `feat: ...`, `fix: ...`, `refactor: ...`, `chore: ...`, `docs: ...`, `test: ...`
- DDL은 `src/main/resources/db/migration/V*.sql` (Flyway)에만 작성. 엔티티 변경 시 마이그레이션 추가.
