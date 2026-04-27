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

### 3.1 패키지 구조 (도메인 중심)
```
com.sseulang
├── global/        config, security, exception, common, infra
└── domain/        user, auth, item, transaction, payment, point,
                   delivery, chat, notification, report, review,
                   notice, banner, admin, category
```
각 도메인 하위: `controller / service / repository / entity / dto / event`

### 3.2 명명 규칙
- 패키지: 소문자, 단수형 (`item`, `user` ✅ / `items`, `users` ❌)
- 클래스: PascalCase
- DTO 접미사: `Request`, `Response` (예: `ItemCreateRequest`, `ItemResponse`)
- 엔티티 = 테이블명 단수 PascalCase (`Item`, `Transaction`)
- 메서드: camelCase, 동사로 시작
- 상수: SCREAMING_SNAKE_CASE
- 테스트: `대상메서드_상황_기대결과` (예: `signup_이메일중복시_예외발생`)

### 3.3 레이어 룰 (절대 금지)
- ❌ Controller가 Repository 직접 호출
- ❌ Service가 다른 도메인 Repository 직접 호출 → **다른 도메인 Service를 통해**
- ❌ Entity를 Controller까지 노출 → 무조건 DTO 변환
- ❌ Service에서 HttpServletRequest 받기 → Controller에서 추출해 전달
- ❌ Entity 안에 Setter 사용 → 비즈니스 메서드로 상태 변경
- ✅ Entity 생성/수정은 정적 팩토리 메서드 또는 도메인 메서드로

### 3.4 트랜잭션
- `@Transactional`은 **Service에만** (Controller, Repository에 X)
- 조회 전용은 `@Transactional(readOnly = true)`
- 클래스 단위 readOnly 기본 + 쓰기 메서드만 `@Transactional` 오버라이드 패턴 권장

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

- **Access Token**: 30분, JWT, payload = `userId + role + jti`
- **Refresh Token**: 7일, Redis 저장, **Rotation 적용**
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

## 7. 테스트 전략

### 7.1 우선순위
- **단위 테스트 필수**: 결제, 거래 상태 전이, 포인트 동시성, JWT 검증, 토큰 Rotation
- **통합 테스트**: Controller 레벨 핵심 플로우 (회원가입, 로그인, 물품 등록, 결제)
- CRUD: Swagger 수동 테스트로 갈음 OK

### 7.2 도구
- JUnit 5 + Mockito + AssertJ
- Testcontainers (MySQL, MongoDB)
- `@SpringBootTest` 무분별 사용 X — 가능하면 Slice 테스트(`@WebMvcTest`, `@DataJpaTest`)

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

## 9. Codex 협업 규칙

### 9.1 역할 분담
- **Claude Code (메인)**: 코드 작성, 수정, 디버깅
- **Codex (리뷰어)**: 작성된 코드 리뷰, 보안 검토, 동시성 검증

### 9.2 Codex 호출 트리거 (자동)
다음 상황에서 **반드시** Codex 리뷰 호출:
1. `global/security/**` 수정 후
2. `domain/payment/**`, `domain/point/**` 수정 후
3. 결제 웹훅, OAuth 콜백 작성 후
4. 동시성 처리 코드 작성 후 (`@Transactional`, 락, 원자 연산)
5. PR 생성 직전 최종 검토

### 9.3 Codex 호출 트리거 (선택)
- 복잡한 비즈니스 로직 작성 후 다른 시각 필요할 때
- 디버깅이 막힐 때
- 성능 의심 시

### 9.4 충돌 방지
- **수정은 Claude Code가, Codex는 리뷰만**
- 둘 다 동시에 같은 파일 수정 금지
- Codex 피드백 받으면 → Claude Code가 반영

### 9.5 토큰 절약
- 매 커밋마다 호출 X
- 위험도 높은 코드만 선별 리뷰
- PR 머지 전 1회 풀 리뷰

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

## 12. 참고 문서

- `SSEULANG_BACKEND_GUIDE.md` — 전체 결정 사항 + Day 1 체크리스트
- `AGENTS.md` — Codex용 동일 컨벤션 (이 파일과 동기화 유지)
- `README.md` — 프로젝트 빌드/실행 방법

---

**중요**: 이 파일과 `AGENTS.md`는 항상 동기화되어 있어야 한다. 컨벤션 추가/변경 시 두 파일 모두 수정.