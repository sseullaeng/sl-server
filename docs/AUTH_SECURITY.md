# Auth 보안 트레이드오프 / 트러블슈팅

> Day 2 JWT 인증 흐름 작업 시 Codex 게이트 1 🔴 리뷰 결과를 정리한 문서.
> 적용한 것 / 의도적으로 미룬 것 / 운영 주의사항.

---

## 1. 게이트 리뷰 결과 요약

### Day 2 (JWT 인증 흐름)
- 게이트 1 thread: `019dd216-bcea-7c42-8cc6-113c6dab9989`
- mini 게이트 2 thread: `019dd22d-72ab-7d91-9602-0672679a2429`

### Day 3 (OAuth + User 도메인)
- 게이트 1 thread: `019dd299-d74b-7ec0-99b5-90d6922df8d4`

`.logs/codex-review.log` 참고. 보안 영역 작업 직후엔 비울 것.

| 항목 | 분류 | 처리 | 커밋/이슈 |
|------|------|------|-----------|
| **게이트 1** C1: AT 블랙리스트 검증 부재 | 🔴 Critical | **적용 (옵션 b — jti 블랙리스트)** | 본 PR |
| **게이트 1** C2: rotation race (isValid+revoke 분리) | 🔴 Critical | **적용 (consume atomic)** | 본 PR |
| **게이트 1** W1: AuthenticationEntryPoint 부재 | 🟡 Warning | **적용** (`JwtAuthenticationEntryPoint` + `JwtAccessDeniedHandler`) | 본 PR |
| **게이트 1** W2: revokeAll SCAN 비효율 | 🟡 Warning | **TODO 주석 + 후속 이슈** | #5 |
| **mini 게이트 2** Critical: Redis 어댑터 통합 테스트 부재 | 🔴 Critical | **적용** — testcontainers Redis IT (blacklist / consume / revokeAll / TTL / race) | 본 PR |
| **mini 게이트 2** Warning: SecurityConfig wiring + traceId 일관성 미검증 | 🟡 Warning | **적용** — `AuthSecurityFlowIT` (WebMvcTest 슬라이스 + traceId 일치 검증) | (Day 2 PR) |
| C1-a 보강: user-level token version | — | **후속 이슈로 분리 (Day 3 User 도메인 후)** | #4 |
| **Day 3 게이트 1** Critical: `findOrCreateBySocial` race | 🔴 | **적용** — `DataIntegrityViolationException` catch + race winner 재조회 | (Day 3 PR) |
| **Day 3 게이트 1** Warning: 외부 OAuth 호출이 트랜잭션 안에서 일어남 | 🟡 | **적용** — `OAuthLoginService` 클래스 `@Transactional` 제거 (가이드 §3.10) | (Day 3 PR) |
| **Day 3 게이트 1** Warning: Google `email_verified=null` 통과 | 🟡 | **적용** — `!Boolean.TRUE.equals` 로 null/false 모두 거부 | (Day 3 PR) |
| **Day 3 게이트 1** Warning: 외부 호출 실패 원인 분리 X | 🟡 | **적용** — `ExternalApiException` 도입 (가이드 §3.10), provider 에서 wrap, service 가 `AUTH_OAUTH_FAILED` 변환 | (Day 3 PR) |
| **Day 3 게이트 1** Suggestion: Email 정규화 부재 | 🟢 | **적용** — Email VO compact constructor 에서 trim + lowercase | (Day 3 PR) |

---

## 2. 의도된 설계 결정 (Critical 아님)

### 2.1 RT 에 role claim 포함

`JwtProvider.issueRefreshToken(userId, role)` — RT payload 에 role 까지 박는다.

**Why:** rotation 시점에 새 AT 발급에 필요한 role 의 출처가 필요한데, User 도메인 + OAuth 흐름은 Day 3 작업. RT 가 가장 단순한 출처.

**한계:** RT 유효 기간(7일) 동안 role 변경(예: `USER → ADMIN` 승급)이 새 AT 에 반영되지 않는다. → 후속 이슈 C1-a 의 token version 도입 시 해결됨.

### 2.2 재사용 탐지 시 사용자 전체 폐기

`RefreshTokenRotationService.rotate` — `consume` 가 false 면 (이미 누가 사용한 jti 또는 처음부터 없는 jti) `revokeAll(userId)` 로 사용자 모든 토큰 무효화.

**Why:** 폐기된 RT 가 다시 들어오는 건 탈취 의심 시그널. 보수적으로 전체 폐기.

**UX 한계:** 동시 refresh 요청이 둘 다 들어와도 한 쪽이 먼저 consume → 다른 쪽이 false → 정상 사용자도 전 디바이스 강제 로그아웃. 동시성 race 의 false-positive.
- 실제 환경에서는 클라이언트가 한 번에 한 RT 만 굴리므로 거의 발생 X
- 발생해도 보안 우선 — 다시 로그인하면 됨

### 2.3 `/auth/login` 엔드포인트 부재

본 PR 은 토큰 발급/검증/회전 프레임워크만 닫고, OAuth 콜백 → User 매핑 → 토큰 발급 흐름은 Day 3 OAuth 작업에서 마무리.

---

## 3. 운영 주의사항

### 3.1 logout 흐름

`POST /api/v1/auth/logout` 는 두 가지 동작:

1. **AT jti 블랙리스트 등록** (Redis `auth:atbl:{jti}`, TTL = AT 잔여 만료시간)
   - logout 직후의 위험 윈도우 차단 — 다른 곳에 AT 가 복사돼 있어도 즉시 폐기됨
2. **RT 폐기** (Redis `auth:rt:{userId}:{jti}` DEL)

폐기 단위는 **device 단위**. 같은 사용자의 다른 디바이스 AT/RT 는 영향 없음.

### 3.2 재사용 탐지 시 사용자 안내

`AUTH_REFRESH_TOKEN_INVALID` 가 나오면 사용자의 **모든 디바이스**가 로그아웃됐다는 의미. 클라이언트는 이 코드를 받으면 강제 재로그인 화면으로 유도하고, 가능하면 "보안상의 이유로 모든 디바이스에서 로그아웃됐습니다" 안내.

### 3.3 `.logs/codex-review.log` 보존 정책

Codex MCP 호출 입출력에 토큰 / 비밀키 / 코드가 그대로 남는다. **보안 영역 작업 직후엔 비울 것:**

```bash
> .logs/codex-review.log
```

`.logs/` 는 `.gitignore` 처리되어 있지만 로컬 디스크엔 남는다.

---

## 4. 후속 이슈 (트래킹)

| ID | 대응 | 시점 |
|----|------|------|
| C1-a | user-level token version (User 엔티티 + AT/RT payload 에 ver claim + Filter 비교) | Day 3 OAuth 작업 ~ 직후 |
| W2 | `RedisRefreshTokenStore.revokeAll` 의 SCAN → user 별 SET 인덱스 (`auth:rt-idx:{userId}`) | 5/6 이후 |

---

## 5. 의존 다이어그램

```
AuthController
   │
   └─> RefreshTokenRotationService  (AT jti 블랙리스트 + RT consume/revoke)
          │
          ├─> JwtProvider           (HS256 issue / parse / validate)
          ├─> RefreshTokenStore     (interface — domain layer)
          │      └─> RedisRefreshTokenStore  (auth:rt:{userId}:{jti})
          ├─> AccessTokenBlacklist  (interface — domain layer)
          │      └─> RedisAccessTokenBlacklist (auth:atbl:{jti})
          └─> Clock

JwtAuthenticationFilter
   ├─> JwtProvider
   ├─> AccessTokenBlacklist  (요청마다 isBlacklisted 검사)
   └─> HandlerExceptionResolver  (BusinessException → ApiResponse 위임)

SecurityConfig (3-chain: ADMIN / USER / PUBLIC)
   ├─> JwtAuthenticationFilter (USER, ADMIN)
   ├─> JwtAuthenticationEntryPoint  (401 → ApiResponse)
   └─> JwtAccessDeniedHandler        (403 → ApiResponse)
```

---

## 6. 관련 문서 / 컨벤션

- `.claude/CLAUDE.md` §4 (보안/인증 핵심 룰), §7.2 (TDD 강제 영역), §9 (Codex 게이트)
- `.claude/AGENTS.md` §3.1 (보안 체크), §6.1 (게이트 1)
- `docs/SSEULANG_BACKEND_GUIDE.md` §4.1 (인증/인가 결정 사항)
