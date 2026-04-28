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

### 2) 환경 변수

`.env.example` 을 복사해서 `.env` 만들고 필요한 값 채우기:

```bash
cp .env.example .env
# 편집기로 열어 JWT_SECRET 등 채우기
```

`.env` 는 `.gitignore` 처리됨. IntelliJ EnvFile 플러그인 또는 `set -a; source .env; set +a` 로 주입.

| 시점 | 채워야 할 변수 |
|------|---------------|
| **현재 (local 개발)** | 채울 것 없음 — `application-local.yml` 의 fallback 으로 시작 가능. `JWT_SECRET` 만 본인 값으로 덮어두면 더 안전. |
| Day 5+ (이미지 업로드) | `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `S3_BUCKET` |
| Day 7 (결제) | `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY` |
| Day 11+ (배포) | prod 의 모든 변수 (`DB_URL`, `COOKIE_DOMAIN`, `MONGO_URI`, `REDIS_HOST`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`) |

> `KAKAO_*` / `GOOGLE_*` 는 가이드 §4.4 의 프론트 주도 OAuth 흐름 기준 백엔드 코드에서 직접 사용하지 않습니다 (백엔드는 access_token 만 검증). 향후 백엔드 주도 OAuth 도입 시 채울 것.

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

## Codex 리뷰 운영

이 프로젝트는 Claude Code(작성) + Codex(리뷰) 듀얼 에이전트로 운영합니다.

- **호출 타이밍 룰**: `.claude/CLAUDE.md` §9 "Codex 협업 규칙" — 게이트 1(🔴 즉시) / 2(🟡 PR 전) / 3(🟢 막혔을 때)
- **실시간 모니터**: 별도 터미널 탭에서 실행
    - Mac / Linux / WSL: `./scripts/codex-watch.sh`
    - Windows: `powershell -File ./scripts/codex-watch.ps1`
- **자세한 사용법**: [docs/CODEX_WATCH.md](docs/CODEX_WATCH.md)

> 보안/결제/포인트 등 게이트 1 영역은 코드 작성 직후 30분 안에 Codex 리뷰 호출이 컨벤션상 의무.
> 단순 CRUD/DTO/설정 추가는 리뷰 거부 영역 — 무분별 호출 금지.
