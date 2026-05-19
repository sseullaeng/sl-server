# CLAUDE.md — 쓸랭(Sseulang) 백엔드 프로젝트

> Claude Code가 매 세션 시작 시 읽는 프로젝트 컨텍스트.
> 상세 결정 사항은 `SSEULANG_BACKEND_GUIDE.md` 참조.

---

## 1. 프로젝트 정체성

- **서비스**: 쓸랭 (Sseulang) — 중고 거래 + 대여 + 나눔 + 배달대행 통합 C2C 플랫폼
- **개발자**: 1명 (백엔드 풀 책임), PM 1명(프론트 겸임)
- **일정**: 백엔드 9일 (4/27 ~ 5/6), 연동·배포 5일 (5/6 ~ 5/11)
- **방침**: A안 풀 도전 — 49개 UC 전부 + 토스페이먼츠 실연동 + WebSocket + MongoDB 처음부터

## 2. 기술 스택 (확정)

```
Java 21 / Spring Boot 3.3.5 / Gradle (Groovy)
JPA(Hibernate) + QueryDSL + Flyway
MySQL 8 / MongoDB 7 / Redis 7
Spring Security + JWT (HttpOnly Cookie)
WebSocket + STOMP
AWS S3 (Presigned URL)
토스페이먼츠 (실연동, 테스트 키)
springdoc-openapi (Swagger)
EC2 + Docker Compose 배포 / Let's Encrypt
```

## 3. 코딩 컨벤션 (강제)

### 3.1 패키지 구조 (DDD-lite)

> 1인 9일 일정 + DDD 핵심(Aggregate / VO / Domain Event / Repository 인터페이스 분리)

```
com.sseulang
├── global/        config, security, exception, common, infra
└── domain/        user, auth, item, transaction, payment, point,
                   delivery, chat, notification, report, review,
                   notice, banner, admin, category
```

각 도메인 하위 4-layer:
```
domain/{도메인}/
├── application/        # UseCase, ApplicationService (트랜잭션 경계)
│   └── dto/            # Command, Query, Result (계층 내부 DTO)
├── domain/             # Aggregate Root, Entity, ValueObject,
│   │                   # DomainService, Repository(인터페이스), DomainEvent
│   └── event/
├── infrastructure/     # JPA RepositoryImpl, 외부 시스템 어댑터
│   └── persistence/
└── presentation/       # Controller, Request/Response DTO
    └── dto/
```

#### DDD-lite 원칙
- **Aggregate Root**만 Repository로 직접 접근. 자식 Entity는 root 통해 조작
- **Value Object** 적극 활용 — `Money`, `Email`, `Phone`, `PointBalance`, `Address` (불변 + 자가 검증)
- **Repository 인터페이스는 `domain/`에**, JPA 구현은 `infrastructure/persistence/`에
- **DomainEvent**는 `domain/event/`에 정의, 발행은 Aggregate Root가, 처리는 ApplicationService 또는 별도 EventListener
- **외부 시스템(토스/카카오/S3)** = `domain/`에 인터페이스만, 어댑터는 `infrastructure/` (anti-corruption)
- **ApplicationService = 트랜잭션 경계 + 흐름 조율**, **DomainService = 여러 Aggregate에 걸친 도메인 규칙**

#### 도입 안 함 (over-engineering)
- 헥사고날 별도 모듈 분리, CQRS, Event Sourcing, 도메인 이벤트 비동기 분산 처리

### 3.2 명명 규칙
- 패키지: 소문자, 단수형 (`item`, `user` ✅ / `items`, `users` ❌)
- 클래스: PascalCase
- DTO 접미사: `Request`, `Response` (예: `ItemCreateRequest`, `ItemResponse`)
- 엔티티 = 테이블명 단수 PascalCase (`Item`, `Transaction`)
- 메서드: camelCase, 동사로 시작
- 상수: SCREAMING_SNAKE_CASE
- 테스트: `대상메서드_상황_기대결과` (예: `signup_이메일중복시_예외발생`)

### 3.3 레이어 룰 (절대 금지)

**의존 방향**: `presentation → application → domain ← infrastructure`
(domain은 어디에도 의존하지 않는다)

- ❌ Presentation(Controller)이 Repository / domain entity 직접 호출 → ApplicationService 경유
- ❌ ApplicationService가 다른 도메인 Repository 직접 호출 → **다른 도메인의 ApplicationService 통해**
- ❌ Domain layer가 Spring/JPA/Web 어노테이션에 의존 (`@Service`, `@Transactional`, `@RestController` 등) → 순수 POJO
- ❌ Aggregate 자식 Entity를 외부에서 직접 조작 → 반드시 Aggregate Root 메서드 통해
- ❌ Entity / Aggregate를 Presentation까지 노출 → Response DTO로 변환
- ❌ Application/Domain Service에서 `HttpServletRequest` 받기 → Controller에서 추출해 전달
- ❌ Entity 안 Setter 사용 → 비즈니스 메서드(상태 전이 의도가 이름에 드러남)로 변경
- ❌ Repository 구현체를 직접 import → 인터페이스만 의존 (구현은 `infrastructure/persistence/`)
- ✅ Entity / Aggregate 생성은 정적 팩토리 메서드 또는 도메인 메서드로
- ✅ ValueObject는 불변 + 생성자에서 자가 검증

### 3.4 트랜잭션
- `@Transactional`은 **ApplicationService에만** (Controller, Repository, Domain Layer에 X)
- 조회 전용은 `@Transactional(readOnly = true)`
- 클래스 단위 readOnly 기본 + 쓰기 메서드만 `@Transactional` 오버라이드 패턴 권장
- DomainService는 트랜잭션을 모름 — 호출하는 ApplicationService가 경계 책임

### 3.5 예외 처리
- 비즈니스 예외: `BusinessException(ErrorCode)` 사용
- `ErrorCode` enum 한 곳에서 관리 (`global/exception/ErrorCode.java`)
- 컨트롤러에서 try-catch 금지 → `GlobalExceptionHandler`가 일괄 처리
- 외부 API 호출 실패는 별도 `ExternalApiException`로 wrap

### 3.6 응답 포맷 (절대 변경 금지)
```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "...", "message": "...", "traceId": "..." } }

// 페이징
{ "success": true, "data": { "content": [], "page": 0, "size": 20,
   "totalElements": 0, "totalPages": 0, "hasNext": false, "hasPrevious": false } }
```
- 모든 컨트롤러는 `ApiResponse<T>` 반환
- 페이징은 `PageResponse<T>` 래퍼 사용

### 3.7 Validation
- 요청 DTO는 `@Valid` + Jakarta Validation 어노테이션
- 비즈니스 규칙은 Service에서 검증 (DTO 검증으로 부족한 것)
- 메시지는 한국어로

### 3.8 로깅
- `@Slf4j` 사용
- 로그 레벨: ERROR(서버 오류), WARN(예상 가능한 비정상), INFO(주요 비즈니스 이벤트), DEBUG(개발용)
- **민감 정보 로깅 절대 금지**: 비밀번호, 토큰, 카드번호, 주민번호
- traceId는 MDC로 자동 주입 → `[%X{traceId}]` 패턴

## 4. 보안 / 인증 핵심 룰

- **Access Token**: 1시간, JWT, payload = `userId + role + jti`
- **Refresh Token**: 14일, Redis 저장, **Rotation 적용**
- **저장**: HttpOnly + Secure + SameSite=Strict 쿠키
- **CSRF**: 쿠키 사용이므로 X-XSRF-TOKEN 헤더로 검증
- **SecurityFilterChain 분리**: USER / ADMIN / PUBLIC 3개
- **에러 코드 정책**:
    - 로그인 실패 → `AUTH_LOGIN_FAILED` 통합 (이메일 존재 여부 노출 X)
    - 토큰 에러 → 구분 (`AUTH_TOKEN_EXPIRED`, `INVALID`, `MISSING`, `REVOKED`)
    - 비즈니스 에러 → 상세 (`ITEM_NOT_FOUND` 등)
    - 서버 에러 → 모호 (`INTERNAL_SERVER_ERROR`, 스택 노출 X)

### 보안 민감 영역 (수정 시 신중)
다음 코드는 수정 시 **반드시 Codex 리뷰 호출**:
- `global/security/**`
- `global/infra/payment/**`
- `domain/auth/**`
- `domain/payment/**`
- `domain/point/**` (잔액 처리)
- 결제 웹훅 핸들러
- OAuth 콜백 핸들러
- WebSocket 인증 흐름 (Spring Security ↔ STOMP 통합)
- 포인트 동시성 처리 코드

## 5. 도메인 핵심 룰

### 5.1 거래 상태 머신
```
채팅중 → 예약 → 거래완료
   └── 취소
```
- 예약 직후: 다른 사용자와 채팅 자동 차단
- 예약 취소 시: 채팅 다시 활성화

### 5.2 포인트 (충전식 — 플랫폼 머니)
- **추가 적립금 X. 실제 결제용 머니.**
- 충전: 토스 결제 → 잔액 증가
- 거래: 구매자 차감 → 판매자 적립 (PG 안 거침)
- 출금: 신청 → 관리자 승인 → 외부 계좌 (시뮬레이션)
- 환불: 거래 취소 시 양쪽 잔액 원복
- **가입 보너스 X / 거래 추가 적립 X** (시스템 적자 방지)

### 5.3 동시성 처리 (필수)
잔액 변경은 **무조건 원자 연산**:
```sql
UPDATE users SET point_balance = point_balance + ?
WHERE id = ? AND point_balance + ? >= 0
```
- 두 사용자 동시 변경 시: **id 작은 사용자 먼저 락** (deadlock 방지)
- 출금: 비관적 락 `SELECT ... FOR UPDATE`
- **모든 잔액 변경 코드에 단위 테스트 필수**

### 5.4 결제 (토스페이먼츠)
- `merchant_uid` UNIQUE → 중복 결제 방지 (멱등성)
- 결제 완료 후 백엔드에서 토스 API로 금액 재검증 (위변조 방지)
- 웹훅 엔드포인트: `POST /api/v1/payments/webhook/toss`
- 카카오페이는 `PaymentGateway` 인터페이스만 추상화 (구현은 향후)

### 5.5 신뢰도 (상호 평가)
- 거래 완료 후 7일 내 양 당사자 별점(1~5) + 한줄평(선택)
- 신뢰도 = 받은 평점 평균
- 초기값 null (리뷰 0건이면 "신규" 표시)

### 5.6 채팅 / 알림
- 채팅방: 1:1 고정 (`chat_rooms.user1_id, user2_id`)
- 메시지: MongoDB
- 알림: MongoDB (시스템 단방향)
- 실시간: WebSocket + STOMP, Spring Security 통합 인증

## 6. API 설계 룰

### 6.1 경로
- 일반 사용자: `/api/v1/{resource}` (resource는 복수형)
- 관리자: `/api/v1/admin/{resource}`
- 인증: `/api/v1/auth/{action}`
- 자원 ID: PathVariable, 필터: Query Parameter

### 6.2 HTTP 메서드
- GET 조회 / POST 생성 / PATCH 부분수정 / PUT 전체수정 / DELETE 삭제
- 상태 변경(예약, 완료)도 PATCH 사용

### 6.3 페이징
- Spring Data `Pageable` 활용
- 응답은 `PageResponse<T>`로 변환
- 채팅 메시지는 **커서 페이징** (`?before={messageId}&size=30`)

## 7. 테스트 전략 (TDD)

### 7.1 흐름 — RED → GREEN → REFACTOR
1. **RED**: 의도를 표현하는 **실패하는 테스트**부터 작성. 컴파일 에러도 RED.
2. **GREEN**: 테스트를 통과시키는 **최소 구현**. 우아함보다 통과가 우선.
3. **REFACTOR**: 통과 상태 유지하며 중복 제거 / 이름 정리 / 구조 개선.

### 7.2 강도 (영역별 차등)

| 영역 | 강도 | 위반 시 |
|---|---|---|
| 보안 / 결제 / 포인트 / 거래 상태 전이 / JWT / 토큰 Rotation / 동시성 | 🔒 **테스트 먼저 필수** (구현 전 RED) | PR 리젝, Codex 리뷰 거부 |
| 일반 도메인 로직 (Aggregate 메서드, DomainService, ApplicationService, ValueObject) | TDD 권장 (RED-GREEN-REFACTOR 기본) | 리뷰에서 지적 |
| 트리비얼 어댑터 (Controller 라우팅, JPA Repository, DTO 변환) | 통합 테스트로 갈음 OK | — |

### 7.3 테스트 종류와 적용 범위
- **순수 단위 테스트** (`domain/` layer): Spring 컨텍스트 X, Mockito 거의 X — domain은 외부 의존 0이라 가능
- **단위 테스트 + Mockito** (`application/` layer): 도메인 Repository / 다른 ApplicationService를 mock
- **Slice 테스트**: `@WebMvcTest` (Controller), `@DataJpaTest` (Repository 실제 동작), `@JsonTest` (DTO 직렬화)
- **통합 테스트**: `@SpringBootTest` + Testcontainers — 핵심 플로우(회원가입, 로그인, 결제 end-to-end)에만 한정
- `@SpringBootTest` 남발 금지 — 느림 + TDD 사이클 깨짐

### 7.4 커버리지 (Jacoco)
- 리포트는 항상 생성 (`./gradlew test jacocoTestReport`)
- 임계 강제는 도메인 30% 이상 작성 시 활성화 (build.gradle TODO 참조)
- 점진 상향: 1단계 라인 50% / 보안·결제·포인트 80% → 2단계 70% / 90%

### 7.5 도구
- JUnit 5 + Mockito + AssertJ
- spring-security-test (`@WithMockUser` 등)
- Testcontainers (MySQL, MongoDB)
- 테스트 메서드명: `대상_상황_기대결과` (예: `차감_잔액부족_예외발생`)

### 7.6 동시성 테스트 (필수)
- 잔액 변경 코드는 반드시 동시 실행 시나리오 테스트
- `CompletableFuture` + `CountDownLatch` 또는 `ExecutorService`로 race 재현
- 락/원자 연산이 정상 작동하는지 검증

## 8. Git 컨벤션

### 8.1 브랜치
- `main` (배포) ← `dev` (통합) ← `feature/*` (작업)
- 패턴: `feature/도메인-기능` (예: `feature/payment-toss`, `feature/auth-jwt`)
- main 직접 푸시 금지 (Branch Protection)

### 8.2 커밋
- `feat:` `fix:` `refactor:` `chore:` `docs:` `test:` `style:`
- 한국어 OK, 한 줄로 요약 + 필요 시 본문 부연
- 예: `feat: JWT Refresh Token Rotation 적용`

### 8.3 PR
- `dev` 브랜치로 PR
- GitHub Actions 빌드 통과 필수
- 보안 민감 영역 변경 시 PR 본문에 `@codex review` 표시

## 9. Codex 협업 규칙 (게이트 시스템)

> **핵심 원칙**: 버그가 늦게 발견될수록 손해가 큰 코드일수록 빨리 리뷰한다.
> 무분별한 리뷰는 토큰 낭비 + 노이즈로 진짜 중요한 피드백이 묻힌다.

### 9.1 역할 분담
- **Claude Code (메인)**: 코드 작성, 수정, 디버깅
- **Codex (리뷰어)**: 작성된 코드 리뷰, 보안 검토, 동시성 검증

### 9.2 게이트 1 — 보안/돈 코드 작성 직후 (즉시 리뷰)  🔴

**대상 영역**:
- `global/security/**` (JwtAuthenticationFilter, JwtProvider, SecurityConfig)
- `domain/auth/**` (OAuth2Service, 소셜 로그인 검증)
- `domain/payment/**` (TossPaymentGateway, PaymentWebhookController)
- `domain/point/**` (charge, deduct, refund, 출금 처리)
- 거래 상태 머신 (예약 ↔ 채팅 차단 로직)
- WebSocket 인증 흐름

**호출 시점**: 코드 한 덩어리(보통 한 클래스 또는 한 기능 단위) 끝내자마자 **30분 안에**.

### 9.3 게이트 2 — PR 머지 직전 (feature → dev)  🟡

**대상**: PR 전체 변경사항 (dev 대비 diff)
**호출 시점**: 브랜치 작업 끝, PR 올리기 직전. 영역 간 일관성·아키텍처 정합성까지 본다.

### 9.4 게이트 3 — 막혔을 때 (필요 시)  🟢

**대상**: 막힌 부분만
**호출 시점**: 본인이 **디버깅 30분 이상 막혔을 때**, 또는 설계 판단이 흔들릴 때.
**주의**: 가장 먼저 Codex 호출 X. 일단 30분은 본인이 디버깅 후 호출.

### 9.5 리뷰 안 하는 영역 (Claude만 작업)  ❌

- 단순 CRUD (Item, Wishlist, Block, Notice, Banner)
- DTO 변환 코드, Controller 라우팅
- 카테고리, 해시태그
- 설정 파일 변경 (`application.yml` 항목 추가)
- 마이그레이션 SQL의 단순 추가
- 관리자 페이지 일반 기능 (통계, 회원 목록 조회 등)

### 9.6 영역별 게이트 매핑 (요약 표)

> CLAUDE.md / AGENTS.md 양쪽 동일. 한쪽 변경 시 양쪽 갱신.

| 영역 | 게이트 | 이유 |
|------|--------|------|
| `global/security/**` | 🔴 즉시 | 보안 핵심 |
| `domain/auth/**` (OAuth 포함) | 🔴 즉시 | 토큰/사용자 매핑 결함 위험 |
| `domain/payment/**` | 🔴 즉시 | 결제 = 진짜 돈 |
| `domain/point/**` | 🔴 즉시 | 충전식 머니 = 진짜 돈 |
| 토스 웹훅 핸들러 | 🔴 즉시 | 멱등성/위변조 |
| 거래 상태 머신 (예약/취소) | 🔴 즉시 | 동시 거래 차단 결함 시 분쟁 |
| 출금 처리 | 🔴 즉시 | 락/정합성 |
| WebSocket 인증 | 🔴 즉시 | 인증 누락 시 모든 메시지 노출 |
| MongoDB ↔ MySQL 정합성 | 🟡 PR 전 | 트랜잭션 분리 영역 |
| 검색/필터 (QueryDSL) | 🟡 PR 전 | N+1, 인덱스 |
| Review 시스템 | 🟡 PR 전 | 평균 재계산 정합성 |
| Item CRUD | ❌ 안 함 | 단순 |
| Notice/Banner | ❌ 안 함 | 단순 |
| Wishlist/Block | ❌ 안 함 | 단순 |
| 관리자 일반 기능 | ❌ 안 함 | 단순 |

### 9.7 호출 가이드

#### 30초 셀프 체크

영역 매핑이 애매할 때 다음 셋을 묻는다. **하나라도 YES면 게이트 1 (🔴 즉시), 셋 다 NO면 호출하지 않고 그냥 진행.**

1. 이 코드가 잘못되면 **돈을 잃거나 보안이 뚫리나**?
2. 5/6 이후에 발견되면 **고치는 데 1일 이상** 걸리나?
3. 혼자서는 도저히 검증 못 하는 **동시성 / 암호 / 멱등성** 영역인가?

#### 호출 명령 표준 패턴

**게이트 1 (즉시 리뷰)**
> `{파일/클래스명}` 다 짰어. AGENTS.md 기준으로 codex에 보안 리뷰 요청해줘.

**게이트 2 (PR 풀 리뷰)**
> 현재 `{브랜치명}` 브랜치 다 끝났어. dev 대비 변경사항 codex로 풀 리뷰 요청해줘.

**게이트 3 (막혔을 때)**
> `{증상 설명}`. `{파일}` codex한테 보여주고 어디가 문제인지 분석해달라고 해.

### 9.8 충돌 방지
- **수정은 Claude Code가, Codex는 리뷰만**
- 둘 다 동시에 같은 파일 수정 금지
- Codex 피드백 받으면 → Claude Code가 반영

### 9.9 토큰 절약
- **게이트 외 호출 금지** — 매 커밋·함수마다 호출 X
- 위험도 높은 코드만 선별 리뷰 (게이트 1·2 우선)
- 게이트 3은 본인 디버깅 30분 이후에만

## 10. 개발 흐름 룰

### 10.1 Happy Path First
- 처음 만들 때 정상 케이스만 끝내고, 엣지 케이스는 `// TODO(5/6 이후):` 주석
- 9일 일정에 완벽 추구 X, 동작하는 게 우선

### 10.2 위험 영역 빨리 깨기
- Day 1~3에 외부 의존성(OAuth 콘솔, 토스 키, S3) 모두 검증
- 늦게 발견하면 일정 위험

### 10.3 매일 회고
- 저녁 30분: 끝낸 거 / 못 끝낸 거 / 내일 할 거
- 일정 차질 시 즉시 PM 공유

## 11. 절대 금지 사항

- ❌ `application.yml`에 비밀키 하드코딩 → 환경변수 주입
- ❌ Entity Setter 노출
- ❌ 비즈니스 로직을 Controller나 Repository에 작성
- ❌ 비밀번호 평문 저장 → BCrypt
- ❌ 민감 정보 로깅 (비밀번호, 토큰, 카드번호)
- ❌ N+1 쿼리 방치 → fetch join 또는 `@EntityGraph`
- ❌ `@Transactional` 없이 다중 쓰기
- ❌ 잔액 변경을 비원자 연산으로 처리
- ❌ Entity 직접 직렬화하여 응답
- ❌ 사용자 입력을 그대로 SQL 조립 (QueryDSL/JPQL 파라미터 바인딩 사용)
- ❌ Domain layer에 Spring/JPA 어노테이션 의존 (DDD 위반)
- ❌ Aggregate 자식 Entity 외부 직접 조작 (반드시 Root 통해)
- ❌ 보안/결제/포인트 코드를 테스트 없이 작성 (TDD §7.2 강제 영역)

### Codex 협업 안티패턴 (게이트 위반)
- ❌ 매 커밋마다 Codex 리뷰 (토큰 낭비, 노이즈로 진짜 피드백이 묻힘)
- ❌ 함수 하나 짤 때마다 리뷰 (컨텍스트 부족, 게이트 1 단위 = 클래스/기능)
- ❌ CRUD 무분별 리뷰 (§9.5 거부 영역)
- ❌ 리뷰 받고 무조건 다 반영 (Codex도 틀림, 판단은 개발자)
- ❌ 리뷰만 받고 안 고침 (받았으면 반영 또는 명시적 reject 사유 기록)
- ❌ 막힐 때 가장 먼저 Codex 호출 (먼저 30분 본인 디버깅 후 §9.4 게이트 3)

## 12. 참고 문서

- `SSEULANG_BACKEND_GUIDE.md` — 전체 결정 사항 + Day 1 체크리스트
- `AGENTS.md` — Codex용 동일 컨벤션 (이 파일과 동기화 유지)
- `README.md` — 프로젝트 빌드/실행 방법

---

**중요**: 이 파일과 `AGENTS.md`는 항상 동기화되어 있어야 한다. 컨벤션 추가/변경 시 두 파일 모두 수정.