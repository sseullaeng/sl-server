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
- mini 게이트 2 thread: `019dd2af-c49e-7621-b486-2b541121fe0f`

### Day 8 PR-b (출금 도메인 + 관리자 로그인 + RT role 격리)
- 게이트 1 thread: `019ddc22-822c-72d0-a563-1139b2dfac2e` (4묶음 리뷰: RT role / AdminLogin / Withdrawal / 테스트)

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
| **Day 3 mini 게이트 2** Warning: race catch 광범위 | 🟡 | **적용** — UNIQUE race 만 보정, 그 외 제약 위반(닉네임 등) 은 원본 예외 그대로 throw | (Day 3 PR) |
| **Day 3 mini 게이트 2** Warning: oauth2 엔드포인트 traceId 통합 검증 부재 | 🟡 | **적용** — `AuthOAuthFlowIT` (TraceIdFilter + X-Trace-Id 헤더 ↔ 본문 traceId 일관성, header relay 검증) | (Day 3 PR) |
| **Day 3 mini 게이트 2** Suggestion: OAuthProvider javadoc | 🟢 | **적용** — 예외 계약(ExternalApiException + BusinessException) 명시 | (Day 3 PR) |
| **Day 8 게이트 1** A: revokeAll SCAN→DEL race (동시 save 살아남음) | 🟡 → 🔴 채택 | **적용** — tokenVersion (RT claim `tv` + Redis `auth:rtv:{ROLE}:{userId}`, INCR 으로 한 방 무효화, consume 은 Lua 원자) | Day 8 PR-b |
| **Day 8 게이트 1** A: role normalize default Locale 의존 | 🟡 | **적용** — `Locale.ROOT` 강제 (Redis store + InMemoryFake 양쪽) | Day 8 PR-b |
| **Day 8 게이트 1** B: AdminLogin BCrypt timing leak (missing/inactive 단락 평가) | 🟡 | **적용** — `DUMMY_PASSWORD_HASH` 로 모든 분기에서 `matches()` 강제 호출 | Day 8 PR-b |
| **Day 8 게이트 1** C: user chain `.authenticated()` 만 → ADMIN AT 로 출금 API 진입 가능 | 🔴 Critical | **적용** — `.hasRole("USER")` 강제 + `AuthSecurityFlowIT` 격리 테스트 추가 | Day 8 PR-b |
| **Day 8 게이트 1** C: 출금 신청 멱등성 부재 (네트워크 재시도 → 중복 차감) | 🔴 Critical | **적용** — `WithdrawalRequest.idempotencyKey` (UUID 권장) + V4 `UNIQUE(user_id, idempotency_key)` + select-then-insert dedup + UNIQUE race 패배자 보정 (REQUIRES_NEW 재조회) | Day 8 PR-b |
| **Day 8 게이트 1** C: cancel 의 `FOR UPDATE` 가 권한 검증 전 → 타인 id 로 lock-DoS | 🟡 | **적용** — `findByIdAndUserIdForUpdate(id, userId)` 한 쿼리로 권한+락 동시 획득 (응답은 `WITHDRAWAL_NOT_FOUND` 로 자원 존재 leak X) | Day 8 PR-b |
| **Day 8 게이트 1** D: ADMIN AT → user 엔드포인트 격리 회귀 테스트 부재 | 🔴 Critical | **적용** — `adminAT_user엔드포인트_403`, `roleMissingAT_user엔드포인트_403` | Day 8 PR-b |
| **Day 8 게이트 1** D: legacy RT / lowercase role / 7vs70 SCAN 회귀 | 🟡 | **적용** — `RedisRefreshTokenStoreIT` 에 case 추가 (기존 SCAN 회귀 + role 정규화 + tv mismatch + revokeAll race) | Day 8 PR-b |
| **Day 8 게이트 1** D: 동시 동일 idempotencyKey IT 부재 | 🔴 Critical | **적용** — `WithdrawalSettlementIT.동시_동일_idempotencyKey_1건만` (행 1건 + history 1건만 검증) | Day 8 PR-b |

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

| ID | 대응 | 시점 | 상태 |
|----|------|------|------|
| C1-a | user-level token version (User 엔티티 + AT/RT payload 에 ver claim + Filter 비교) | Day 3 OAuth 작업 ~ 직후 | ✅ Day 8 PR-b 에서 RT 단에 tokenVersion 도입으로 해결 (AT 는 30분 짧아 revoke 부담 적음) |
| W2 | `RedisRefreshTokenStore.revokeAll` 의 SCAN → user 별 SET 인덱스 | 5/6 이후 | ✅ Day 8 PR-b 에서 INCR 한 방으로 변경 — SCAN 자체 제거 |
| Cluster-1 | Redis Cluster 도입 시 multi-key Lua 슬롯 일치를 위한 hash tag `{ROLE:userId}` 적용 | 5/6 이후 (현재 standalone) | 📌 미진행 |
| Withdrawal-1 | 외부 은행 이체 실연동 (현재 `adminComplete` 는 mock 로그만) | 5/6 이후 | 📌 미진행 |

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

### 5.1 Day 8 보강 후 키 패턴

```
auth:rt:{ROLE}:{userId}:{jti}    → marker, TTL = RT 유효기간 (7일)
auth:rtv:{ROLE}:{userId}         → 현재 tokenVersion (Long, INCR-only)
auth:atbl:{jti}                  → AT 블랙리스트, TTL = AT 잔여 만료시간
```

- **role 차원**: USER / ADMIN 동일 숫자 id 공존 시 격리 (예: User#7 ≠ Admin#7).
- **tokenVersion**: revokeAll 은 `INCR rtv` 한 방. 이후 발급된 RT 의 claim.tv 와 mismatch → consume 거부. SCAN race 제거.

---

## 6. 관련 문서 / 컨벤션

- `.claude/CLAUDE.md` §4 (보안/인증 핵심 룰), §7.2 (TDD 강제 영역), §9 (Codex 게이트)
- `.claude/AGENTS.md` §3.1 (보안 체크), §6.1 (게이트 1)
- `docs/SSEULANG_BACKEND_GUIDE.md` §4.1 (인증/인가 결정 사항)

---

## 7. 트러블슈팅 (개발 중 만난 문제 + 해결)

> 코드 보면 결과만 보이고 _왜 이렇게 됐는지_ 안 보이는 결정들. 후속 작업자가 같은 함정 안 밟도록 정리.

### 7.1 RT user/admin 동일 숫자 id 격리 (Day 8 PR-b)

**증상**: User#7 과 Admin#7 가 동시 존재할 수 있는데 Redis 키가 `auth:rt:7:{jti}` 로만 되어 있어 한 쪽 revokeAll 이 다른 쪽 RT 까지 무효화 위험.

**원인**: 초기 설계가 user 도메인만 가정. 관리자 로그인 추가하며 동일 숫자 id 충돌이 가능해짐.

**해결**: `RefreshTokenStore` 인터페이스의 모든 메서드에 `String role` 차원 추가 → 키 `auth:rt:{ROLE}:{userId}:{jti}`. JWT claim 의 role 이 권위 있는 출처. 호출부(OAuthLoginService / AdminLoginService / RefreshTokenRotationService) 전부 갱신.

### 7.2 revokeAll SCAN→DEL race (Day 8 PR-b — Codex 지적)

**증상**: `revokeAll` 이 SCAN 으로 키 목록을 모은 뒤 DEL. 그 사이에 동시 `consume` → `save` 가 끼어들어 새 RT 가 살아남는 race.

**원인**: SCAN 은 atomic 이지만 SCAN 이후 새로 SET 되는 키는 보지 못함.

**해결**: tokenVersion 도입.
- 각 (role, userId) 마다 단조 증가 정수 `tv` 를 Redis 에 보관 (`auth:rtv:{ROLE}:{userId}`)
- RT 발급 시 현재 tv 를 JWT claim `tv` 에 박음
- `revokeAll` = `INCR rtv` 한 방 — SCAN 제거
- `consume` 은 Lua 로 `(claim.tv == GET rtv)` 검증 + DEL jti 원자 실행
- 이전 SCAN 중 끼어들었어도 INCR 이후엔 모든 RT 가 mismatch → 거부

**부수 효과**: 기존 RT (tv claim 없음) 전부 무효화. dev 단계라 backward compat 미고려.

**회귀 방지**: `RedisRefreshTokenStoreIT.revokeAll_race_새로_save된_RT도_무효화` — revokeAll 진행 중 동시 save 들이 모두 거부됨을 검증.

### 7.3 user chain `.authenticated()` 만 → ADMIN AT 권한 누수 (Day 8 PR-b)

**증상**: SecurityConfig 의 user chain 이 `.anyRequest().authenticated()` 만 요구. ADMIN AT 도 인증된 상태라 통과 — 출금/결제 같은 user 영역에 ADMIN AT 로 접근 가능.

**위험**: adminId 와 userId 가 같은 숫자면 `@AuthenticationPrincipal Long userId` 본인 검증도 통과해 사용자 자원 조작 가능.

**해결**: `.anyRequest().hasRole("USER")` 강제. ADMIN AT 는 admin chain 만 통과. 회귀: `AuthSecurityFlowIT.adminAT_user엔드포인트_403`.

### 7.4 출금 신청 멱등성 부재 (Day 8 PR-b)

**증상**: 더블클릭/네트워크 재시도로 같은 출금이 두 번 신청 → 잔액 두 번 차감.

**해결**: 클라이언트 발급 `idempotencyKey` (UUID 권장) + DB 제약 `UNIQUE(user_id, idempotency_key)`. ApplicationService 에서 select-then-insert dedup 로직 + UNIQUE 제약 race 패배자는 catch 후 기존 row id 반환.

**구현 중 만난 추가 함정 3가지** (멱등성 구현 자체보다 더 많은 시간 소비):

#### 7.4.1 MySQL InnoDB: duplicate-key vs deadlock 모두 발생

**증상**: 동시 INSERT 가 같은 UNIQUE 키를 노릴 때 MySQL 이 (a) `Duplicate entry` 또는 (b) `Deadlock found` 둘 중 하나를 던짐. 어느 쪽인지는 타이밍에 따라 달라짐.

**원인**: InnoDB 의 next-key 락이 같은 UNIQUE 키 영역에 gap 락을 걸면서 두 트랜잭션이 서로의 lock 을 기다리면 deadlock 으로 처리. 그렇지 않으면 immediate duplicate-key.

**해결**: catch 절에서 `DataIntegrityViolationException | CannotAcquireLockException` 두 타입 모두 처리. 둘 다 dedup 시그널로 동일 취급.

#### 7.4.2 Hibernate: 같은 tx 안에서 INSERT 실패 catch → session dirty

**증상**: `try { repo.save(entity) } catch (...) { ... }` 패턴이 `org.hibernate.AssertionFailure: null id ... (don't flush the Session after an exception occurs)` 로 깨짐.

**원인**: JPA persistence context 에 entity 가 이미 추가된 상태에서 flush 가 실패. 이후 같은 tx 가 commit 되며 다시 flush 를 시도 → entity 가 여전히 dirty 상태 → 실패.

**해결**: INSERT 시도를 별도 트랜잭션(`@Transactional(propagation = REQUIRES_NEW)`)으로 격리. 실패해도 그 inner tx 만 롤백되고 outer 의 persistence context 는 영향 없음. 같은 클래스 안의 자기 호출은 Spring AOP 가 가로채지 못하므로 `@Lazy WithdrawalApplicationService self` 자기 주입 패턴 사용.

#### 7.4.3 REPEATABLE_READ: 패배자가 winner commit 못 봄

**증상**: race 패배자가 catch 후 `findByUserIdAndIdempotencyKey` 로 winner 의 row 를 찾으려 하지만 빈 결과 → 원본 예외 재throw.

**원인**: outer 트랜잭션이 MySQL 기본 isolation `REPEATABLE_READ` 라서 tx 시작 시점의 snapshot 을 고수. winner 가 catch 시점 이후에 commit 하더라도 outer tx 의 snapshot 에는 없음.

**해결**: 복구 SELECT 도 `REQUIRES_NEW` 로 새 트랜잭션 열어서 fresh snapshot 받음. `findIdempotentInFreshTx(cmd, originalException)` 메서드로 분리.

**최종 구조**:
```
request(cmd)                                       ← outer tx (readonly, class default)
  ├─ findByUserIdAndIdempotencyKey (fast path)    ← outer 에서 1회 조회
  ├─ self.insertWithDeduct(cmd)                   ← REQUIRES_NEW write tx
  │    ├─ save(Withdrawal)
  │    └─ pointApplicationService.deduct
  └─ catch DataIntegrity|CannotAcquireLock
       └─ self.findIdempotentInFreshTx(cmd, e)    ← REQUIRES_NEW readonly, fresh snapshot
```

### 7.5 cancel 의 lock-DoS (Day 8 PR-b — Codex 지적)

**증상**: `cancel(id, requesterId)` 가 `findByIdForUpdate(id)` 로 락 먼저 잡고 `isOwnedBy` 검증을 나중에 함. 공격자가 임의 id 로 cancel 호출하면 잠깐이지만 모두에게 락 걸 수 있음.

**해결**: `findByIdAndUserIdForUpdate(id, userId)` 로 권한 검증과 락을 한 쿼리에 묶음. 타인 자원이면 빈 결과 → 락 미획득. 응답은 `WITHDRAWAL_NOT_FOUND` 로 자원 존재 여부도 leak 안 되게.

### 7.6 AdminLogin BCrypt timing leak (Day 8 PR-b — Codex 지적)

**증상**: `if (!admin.isActive() || !passwordEncoder.matches(...))` 단락 평가 → username 미존재면 BCrypt 안 돌고, inactive 면 matches 도 안 돎. 응답 시간 차이로 username 존재 여부 / active 여부 leak.

**해결**: `DUMMY_PASSWORD_HASH` 상수 도입. 미존재여도 dummy hash 로 항상 `passwordEncoder.matches(password, hash)` 호출 → 모든 분기에서 ~50ms BCrypt 비용 균일. 회귀: `AdminLoginServiceTest.login_미존재` / `login_비활성` 에 `verify(passwordEncoder).matches(...)` 추가.

### 7.7 InMemoryFakeRefreshTokenStore 의미 변경으로 기존 테스트 깨짐

**증상**: tokenVersion 도입 후 기존 `RefreshTokenRotationServiceTest.rotate_재사용탐지` 가 `store.contains(jti)` 로 무효화 검증하던 게 false positive (jti 키는 살아있고 tv mismatch 로 거부됨).

**원인**: 새 의미상 `revokeAll` 은 INCR 만 — jti 키는 TTL 만료까지 잔존하지만 의미상 무효. raw key existence 와 logical validity 가 분리됨.

**해결**: 테스트도 의미 기반으로 변경. `service.rotate(rt)` 가 `AUTH_REFRESH_TOKEN_INVALID` 로 거부되는지를 검증 — 추상화 누수 제거.

### 7.8 role normalize default Locale 의존 (Day 8 PR-b — Codex 지적)

**증상**: `role.toUpperCase()` 가 default Locale 사용. 터키어 등 일부 locale 에서 `i` ↔ `İ` 변환이 ASCII 와 달라 키 정규화가 깨질 수 있음.

**해결**: `role.toUpperCase(Locale.ROOT)` 강제 (Redis store + InMemoryFake 양쪽).

---

## 8. 운영 plays (auth/withdrawal 사고 나면)

| 증상 | 1차 확인 | 보정 |
|------|----------|------|
| 특정 사용자 "로그인 풀림" 신고 폭주 | `auth:rtv:USER:{userId}` INCR 이력 (Redis log) — 누군가 RT 재사용 탐지로 revokeAll 트리거됐는지 | 본인 재로그인 안내. 의심되면 잠시 active=false 후 조사 |
| 출금 중복 차감 신고 | `withdrawals` 테이블에서 (user_id, idempotency_key) 페어 — 같은 key 로 행 2개면 UNIQUE 누락 의심 | V4 마이그레이션 적용 여부 확인. 행 1개면 신고자 오해 / 다른 트랜잭션 |
| Admin chain 으로 user API 접근 가능 신고 | `SecurityConfig.userFilterChain` 의 `.hasRole("USER")` 회귀 여부 | 빠진 경우 즉시 핫픽스. 회귀 IT 추가 |
| 출금 신청이 자꾸 NOT_FOUND 응답 | `findByIdAndUserIdForUpdate` 가 본인 자원만 조회 — 클라이언트가 잘못된 id 보내거나 다른 사용자 id 사용 중 | 클라이언트 로깅 확인 |
| Withdrawal IT deadlock 빈발 | 동시 INSERT 패턴 — UNIQUE race 패배자 catch 가 정상 동작하는지 | `WithdrawalApplicationService.request` 의 catch 절에 `DataIntegrityViolationException` 와 `CannotAcquireLockException` 두 타입 모두 살아있는지 |
