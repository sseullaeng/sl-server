# 쓸랭(Sseulang) 백엔드 프로젝트 가이드

> **마지막 업데이트**: 2026-04-27
> **작성 목적**: IntelliJ에서 바로 작업 시작 가능하도록 모든 결정 사항 + Day 1 체크리스트 정리

---

## 1. 프로젝트 개요

- **서비스명**: 쓸랭 (Sseulang)
- **한 줄 요약**: 중고 거래 + 대여 + 나눔 + 배달대행을 통합한 C2C 플랫폼
- **유스케이스**: 49개 (일반 사용자 39개 + 관리자 10개)
- **DB 엔티티**: 20개 테이블 (MySQL 19개 + MongoDB 2개 컬렉션)
- **개발 일정**: 백엔드 9일 (4/27 ~ 5/6) + 프론트 연동·배포 5일 (5/6 ~ 5/11)
- **방향성**: A안 풀 도전 — 49개 UC 전부 구현 + 토스페이먼츠 실연동 + WebSocket + MongoDB 처음부터

---

## 2. 기술 스택

| 영역 | 선택 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.3 |
| 빌드 | Gradle |
| ORM | JPA(Hibernate) + QueryDSL |
| 마이그레이션 | Flyway |
| RDBMS | MySQL 8.x |
| NoSQL | MongoDB 7.x (메시지, 알림) |
| 캐시 | Redis (Refresh Token, AT 블랙리스트, 분산 락) |
| 인증 | Spring Security + JWT (HttpOnly Cookie) |
| 소셜 로그인 | Spring OAuth2 Client (카카오, 구글) — 프론트 주도 흐름 |
| 결제 | 토스페이먼츠 (실연동, 테스트 키) |
| 실시간 | WebSocket + STOMP |
| 파일 저장 | AWS S3 + Presigned URL |
| 배포 | AWS EC2 + Docker Compose |
| HTTPS | Let's Encrypt (certbot) |
| 도메인 | 가비아 (구현 후 발급) |
| API 문서 | springdoc-openapi (Swagger UI) |

---

## 3. 패키지 구조

```
com.sseulang
├── SseulangApplication.java
├── global/
│   ├── config/        SecurityConfig, S3Config, RedisConfig, MongoConfig, OpenApiConfig, WebSocketConfig
│   ├── security/      JwtProvider, JwtAuthenticationFilter, UserPrincipal, OAuth2Service
│   ├── exception/     GlobalExceptionHandler, ErrorCode, BusinessException
│   ├── common/        BaseEntity, ApiResponse, PageResponse, TraceIdFilter
│   └── infra/
│       ├── s3/        S3Uploader, PresignedUrlGenerator
│       ├── payment/   PaymentGateway (interface), TossPaymentGateway
│       └── delivery/  DeliveryGateway (interface), MockDeliveryGateway
└── domain/
    ├── user/          User, UserController, UserService, UserRepository
    ├── auth/          AuthController, AuthService (로컬+소셜)
    ├── admin/
    ├── category/      (self-ref)
    ├── item/          Item, ItemImage, ItemHashtag, Wishlist
    ├── transaction/
    ├── payment/       Payment, Charge, Withdrawal
    ├── point/         PointHistory
    ├── delivery/
    ├── chat/          ChatRoom (MySQL) + Message (MongoDB)
    ├── notification/  Notification (MongoDB)
    ├── report/        UserBlock, UserReport
    ├── review/        Review (신규 추가)
    ├── notice/
    └── banner/
```

각 도메인 폴더 내부: `controller / service / repository / entity / dto / event` 분리

---

## 4. 핵심 설계 결정 사항

### 4.1 인증/인가
- **Access Token**: 30분 (JWT, payload: userId, role, jti)
- **Refresh Token**: 7일 (Redis 저장, Rotation 적용)
- **저장 방식**: HttpOnly + Secure + SameSite=Strict 쿠키
- **로그아웃**: Redis RT 삭제 + AT 블랙리스트 (jti 기준, 남은 만료시간만큼)
- **CSRF 대응**: 쿠키 사용하므로 CSRF 토큰 별도 헤더(X-XSRF-TOKEN) 검증
- **Security 체인 분리**: USER용 / ADMIN용 / PUBLIC용 SecurityFilterChain 3개

### 4.2 에러 응답 정책
- **로그인 실패**: 통합 (`AUTH_LOGIN_FAILED`) — 이메일 존재 여부 노출 X
- **토큰 에러**: 구분 (`AUTH_TOKEN_EXPIRED`, `INVALID`, `MISSING`, `REVOKED`)
- **비즈니스 에러**: 상세 (`ITEM_NOT_FOUND`, `INSUFFICIENT_POINT` 등)
- **서버 에러**: 모호 (`INTERNAL_SERVER_ERROR` 통합, 스택 트레이스 노출 X)
- **traceId**: 모든 에러 응답 + 응답 헤더(`X-Trace-Id`)에 포함, MDC로 로그에 자동 삽입

### 4.3 공통 응답 포맷
```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "ITEM_NOT_FOUND", "message": "...", "traceId": "abc-123" } }

// 페이징 (PageResponse<T>)
{ "success": true, "data": { "content": [...], "page": 0, "size": 20, "totalElements": 153, "totalPages": 8, "hasNext": true, "hasPrevious": false } }
```

### 4.4 소셜 로그인 흐름 (프론트 주도)
1. 프론트가 카카오/구글 SDK로 access_token 획득
2. `POST /api/v1/auth/oauth2/{provider}` Body: `{ accessToken: "..." }`
3. 백엔드가 provider API로 토큰 검증 + 사용자 정보 조회
4. users 테이블 조회/생성 (`social_provider` + `social_id`)
5. JWT 발급 → HttpOnly Cookie 세팅

**전화번호 정책**:
- 로컬 회원가입: 필수 (`@NotBlank` DTO 검증)
- 소셜 로그인: nullable 허용
- DB: `users.phone`은 nullable

### 4.5 이미지 업로드 (Presigned URL)
- **발급 시 제약**: Content-Type(image/*), Content-Length(≤5MB), 만료 5분
- **사후 검증**: 파일 등록 시 백엔드가 S3 HEAD 요청으로 실제 메타데이터 확인
- **파일명**: 백엔드에서 UUID 생성 (사용자 입력 무시)
- **접근**: public-read
- **CDN**: MVP엔 S3 직접, 시간 남으면 CloudFront 추가
- **폴더 구조**:
  ```
  sseulang-bucket/
  ├── profiles/{userId}/{uuid}.jpg
  ├── items/{itemId}/{uuid}.jpg          (최대 10장)
  ├── messages/{roomId}/{uuid}.jpg
  ├── notices/{noticeId}/{uuid}.jpg
  └── banners/{bannerId}/{uuid}.jpg
  ```

### 4.6 검색 (UC-17, UC-18)
- **엔진**: MySQL FULLTEXT INDEX (ngram parser)
- **검색 대상**: `items.title` + `items.description`
- **해시태그 필터**: `item_hashtags` JOIN, 다중 태그는 AND 조건
- **기본 정렬**: 최신순(`createdAt DESC`)
- **동적 쿼리**: QueryDSL `BooleanBuilder`
- **자동완성**: 안 만듦 (5/6 이후)

### 4.7 신뢰도 (상호 평가)
- **모델**: 거래 완료 후 양 당사자가 별점(1~5) + 한줄평(선택) 작성
- **신뢰도 계산**: 받은 리뷰 평점의 평균을 `users.trust_score`에 갱신 (리뷰 작성 시점)
- **평가 기한**: 거래 완료 후 7일
- **평가 강제도**: 유도 (알림으로 권유, 강제 X)
- **초기값**: null (리뷰 0건이면 "신규" 표시)
- **공개 범위**: 평균 점수 + 리뷰 개수만 (한줄평은 본인만)
- **신고와의 관계**: 별개 (신고는 관리자가 `is_blocked` 플래그로 제재)

### 4.8 포인트 (충전식 — 플랫폼 머니)
- **개념**: 추가 적립금 X. **실제 결제에 쓰는 머니**.
- **충전**: 토스페이먼츠로 결제 → `point_balance` 증가
- **거래 결제**: 구매자 포인트 차감 → 판매자 포인트 적립 (PG 안 거침)
- **출금**: 판매자가 출금 신청 → 관리자 승인 → 외부 계좌 이체 (시뮬레이션)
- **환불**: 거래 취소 시 양쪽 잔액 원복
- **가입 보너스 X** / **거래 완료 추가 적립 X** (시스템이 진짜 돈 뿌리면 적자)
- **만료**: 없음

**동시성 처리 (필수)**:
- 잔액 변경: `UPDATE users SET point_balance = point_balance + ? WHERE id = ? AND point_balance + ? >= 0` (원자 연산 + 음수 방지)
- 두 사용자 동시 잔액 변경 시 deadlock 방지: **id 작은 사용자 먼저 락**
- 출금 처리: 비관적 락 (`SELECT ... FOR UPDATE`)
- **단위 테스트 필수** (포인트 정합성 깨지면 사고)

### 4.9 결제 (토스페이먼츠 실연동)
- **PG**: 토스페이먼츠 (사업자 없이 테스트 키 발급 가능)
- **수단**: 카드 / 계좌이체 / 가상계좌
- **카카오페이**: `PaymentGateway` 인터페이스만 추상화, 향후 사업자 등록 시 구현체 추가
- **흐름**: 결제 요청 → 인증 → 승인 → 웹훅 → 환불
- **멱등성**: `merchant_uid` UNIQUE로 중복 결제 방지
- **검증**: 결제 완료 후 백엔드에서 토스 API로 금액 재검증 (위변조 방지)
- **웹훅 엔드포인트**: `POST /api/v1/payments/webhook/toss`

### 4.10 채팅 / 알림
- **채팅방**: 1:1 고정 (`chat_rooms.user1_id, user2_id`)
- **메시지**: MongoDB `messages` 컬렉션
- **알림**: MongoDB `notifications` 컬렉션 (시스템 → 사용자 단방향 스트림)
- **실시간**: WebSocket + STOMP (Spring Security 통합)
- **메시지 페이징**: 커서 기반 (`?before={messageId}&size=30`)

**MongoDB ↔ MySQL 정합성**:
- 메시지 발신: MongoDB insert → MySQL `chat_rooms.last_message_at` 갱신 → 알림 생성
- 트랜잭션 분리, 실패 시 보상 로직 X (로그 + 재시도)
- 채팅 메시지는 잃어도 비즈니스 크리티컬 X

### 4.11 거래 정책
- **상태 머신**: `채팅중 → 예약 → 거래완료` (또는 `취소`)
- **동시 거래**:
    - 예약 전: 한 물품에 여러 명과 채팅 OK
    - **예약 직후: 다른 사용자와의 채팅 차단**
    - 예약 취소되면 채팅 다시 활성화
- **거래 타입**: `대여`, `판매`, `나눔` (ERD 통일)

### 4.12 배달대행
- **방식**: 외부 대행사 API 연동 X, **상태 전이 시뮬레이션**
- **인터페이스 추상화**: `DeliveryGateway` (향후 부릉/바로고 등 연동 가능)
- **기사 위치**: `driver_lat`, `driver_lng` (DECIMAL(10,7)) — 단일 컬럼 분리
- **상태 머신**: `신청 → 배달중 → 완료` (또는 `취소`)

### 4.13 보증금 처리
- 대여 거래 결제 시 `payments` 두 건: `대여금` + `보증금`
- 대여 종료 시 보증금 반환은 **관리자 수동 처리** (UC-43)
- 환불은 토스 API 또는 포인트 복원

---

## 5. 데이터베이스 (MySQL + MongoDB)

### 5.1 MySQL 테이블 (19개)

| # | 테이블 | 설명 |
|---|--------|------|
| 1 | users | 사용자 |
| 2 | admins | 관리자 |
| 3 | categories | 카테고리 (self-ref) |
| 4 | items | 물품 |
| 5 | item_images | 물품 이미지 |
| 6 | item_hashtags | 물품 해시태그 |
| 7 | wishlists | 관심목록 |
| 8 | transactions | 거래 |
| 9 | payments | 결제 (충전 + 거래 결제 통합) |
| 10 | delivery_requests | 배달대행 |
| 11 | chat_rooms | 채팅방 (메타데이터만) |
| 12 | user_blocks | 차단 |
| 13 | user_reports | 신고 |
| 14 | point_histories | 포인트 내역 |
| 15 | notices | 공지/이벤트 |
| 16 | banners | 메인 배너 |
| 17 | reviews | **신규** — 거래 후 상호 평가 |
| 18 | withdrawals | **신규** — 출금 신청 |
| 19 | trust_score_histories | **신규** — 신뢰도 변동 이력 (선택, 시간 남으면) |

### 5.2 MongoDB 컬렉션 (2개)

| 컬렉션 | 용도 |
|--------|------|
| messages | 채팅 메시지 |
| notifications | 시스템 알림 |

### 5.3 ERD 변경 사항 (원본 ERD 대비)

#### 일괄 변경
1. `chat_rooms`: `sender_id, receiver_id` → **`user1_id, user2_id`**
2. `notifications` 테이블 → **MongoDB로 이전 (MySQL DDL에서 제외)**
3. `wishlists`에 `(user_id, item_id)` UNIQUE 추가
4. `delivery_requests.driver_location` → **`driver_lat`, `driver_lng`** (DECIMAL(10,7))
5. `user_reports`에 CHECK 제약: `reported_id` OR `item_id` 둘 중 하나는 NOT NULL
6. `items.trade_type` ENUM: `'대여','판매','나눔'`
7. `transactions.trade_type` ENUM 통일: `'대여','판매','나눔'` ('구매' → '판매')
8. `users.email` UNIQUE 제약 추가
9. `users.(social_provider, social_id)` UNIQUE 복합 인덱스 추가
10. `users.phone` NOT NULL → **NULL 허용**
11. `users.trust_score` DEFAULT 0.00 → **nullable**
12. `items.title`, `items.description` FULLTEXT INDEX (ngram parser)
13. 자주 조회되는 FK + `created_at` 컬럼들에 인덱스 일괄 추가

#### 신규 테이블 추가
14. **`reviews`** (id, transaction_id, reviewer_id, reviewee_id, rating, comment, created_at) — UNIQUE(transaction_id, reviewer_id)
15. **`withdrawals`** (id, user_id, amount, bank_name, account_number, account_holder, status, admin_id, admin_memo, requested_at, processed_at)

#### 컬럼 추가/변경
16. `payments.payment_key VARCHAR(200)` 추가 (토스 결제 키)
17. `payments.merchant_uid VARCHAR(100) UNIQUE` 추가 (우리 측 주문 고유 ID)
18. `payments.status` ENUM 확장: `'대기','진행중','완료','실패','환불진행중','환불완료','환불실패'`
19. `payments.payment_type` ENUM 확장: `'충전','대여금','보증금','수수료','환불'`
20. `point_histories.point_type` ENUM 확장: `'충전','결제','판매정산','출금','환불'`

---

## 6. API 경로 규칙

### 6.1 일반 사용자 API
```
# 인증
POST   /api/v1/auth/signup
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/refresh
POST   /api/v1/auth/oauth2/{provider}      # provider = kakao | google

# 사용자
GET    /api/v1/users/me
PATCH  /api/v1/users/me
DELETE /api/v1/users/me
GET    /api/v1/users/{id}                  # 다른 사용자 프로필

# 물품
GET    /api/v1/items                       # 목록 + 검색·필터·페이징
POST   /api/v1/items
GET    /api/v1/items/{id}
PATCH  /api/v1/items/{id}
DELETE /api/v1/items/{id}
POST   /api/v1/items/{id}/wishlist
DELETE /api/v1/items/{id}/wishlist
POST   /api/v1/items/{id}/report

# 채팅
GET    /api/v1/chat-rooms
POST   /api/v1/chat-rooms                  # 물품 ID로 채팅방 생성
GET    /api/v1/chat-rooms/{id}/messages    # 커서 페이징
POST   /api/v1/chat-rooms/{id}/messages

# 거래
POST   /api/v1/transactions
PATCH  /api/v1/transactions/{id}           # 예약/완료/취소

# 결제 (토스 연동)
POST   /api/v1/payments/charge             # 충전 시작
POST   /api/v1/payments/charge/confirm     # 충전 승인 (콜백)
POST   /api/v1/payments/webhook/toss       # 웹훅

# 포인트
GET    /api/v1/points/balance
GET    /api/v1/points/histories
POST   /api/v1/withdrawals                 # 출금 신청
GET    /api/v1/withdrawals
DELETE /api/v1/withdrawals/{id}            # 신청 상태일 때만

# 배달대행
POST   /api/v1/deliveries
GET    /api/v1/deliveries/{id}
PATCH  /api/v1/deliveries/{id}

# 알림 / 차단 / 리뷰
GET    /api/v1/notifications
PATCH  /api/v1/notifications/{id}/read
POST   /api/v1/blocks
DELETE /api/v1/blocks/{userId}
POST   /api/v1/reviews
GET    /api/v1/users/{id}/reviews
GET    /api/v1/users/me/reviews/pending

# 공지 / 배너
GET    /api/v1/notices
GET    /api/v1/notices/{id}
GET    /api/v1/banners

# 파일
POST   /api/v1/files/presigned-url         # purpose, files[]
```

### 6.2 관리자 API
```
POST   /api/v1/admin/auth/login
GET    /api/v1/admin/stats

GET    /api/v1/admin/users
PATCH  /api/v1/admin/users/{id}/block

GET    /api/v1/admin/items
PATCH  /api/v1/admin/items/{id}            # 비공개/삭제

GET    /api/v1/admin/reports
PATCH  /api/v1/admin/reports/{id}

POST   /api/v1/admin/transactions/{id}/refund-deposit   # 보증금 반환

GET    /api/v1/admin/withdrawals
PATCH  /api/v1/admin/withdrawals/{id}      # 출금 승인/거부

GET    /api/v1/admin/deliveries

POST   /api/v1/admin/notices
PATCH  /api/v1/admin/notices/{id}
DELETE /api/v1/admin/notices/{id}

POST   /api/v1/admin/banners
PATCH  /api/v1/admin/banners/{id}
DELETE /api/v1/admin/banners/{id}
```

---

## 7. 협업 / Git 전략

### 7.1 브랜치 전략
```
main         (배포용, 항상 동작 보장)
 └── dev     (통합 브랜치)
      ├── feature/auth-jwt
      ├── feature/item-crud
      ├── feature/payment-toss
      └── ...
```

### 7.2 룰
- `feature/*`는 `dev`에서 따고, PR로 `dev`에 머지
- `dev` 안정 시 → `main` 머지 (배포 시점)
- `main` 직접 푸시 금지 (Branch Protection)
- PR 머지 전 GitHub Actions 빌드 통과 필수
- 1인 작업이지만 PR로 자기 코드 리뷰 의식 유지

### 7.3 커밋 컨벤션
- `feat:` 새 기능
- `fix:` 버그 수정
- `refactor:` 리팩토링
- `chore:` 빌드/설정/패키지
- `docs:` 문서
- `test:` 테스트
- `style:` 포매팅

### 7.4 브랜치 네이밍
- `feature/도메인-기능` (예: `feature/item-search`, `feature/payment-toss`)
- `fix/이슈명`
- `refactor/대상`

### 7.5 협업
- **API 명세**: Swagger UI URL 공유 (실시간 자동 갱신)
- **이슈 트래킹**: GitHub Issues 또는 Notion (편한 거)
- **PM(프론트)과 합의**: 5/6 이전에 Swagger URL 공유, API 명세 변경 시 즉시 알림

---

## 8. 일정 (9일)

| Day | 날짜 | 작업 |
|-----|------|------|
| 1 | 4/27 (일) | **세팅**: Spring Boot 프로젝트 생성, Docker Compose, Flyway 초기 스키마, GlobalExceptionHandler, BaseEntity, traceId 필터, Swagger, GitHub Actions, **카카오/구글 OAuth 콘솔 등록** |
| 2 | 4/28 (월) | User/Admin 도메인, JWT, SecurityConfig, 로컬 회원가입/로그인/로그아웃, RT Rotation |
| 3 | 4/29 (화) | OAuth2 (카카오/구글), Category, Item CRUD |
| 4 | 4/30 (수) | Item 검색/필터 (FULLTEXT + QueryDSL), S3 Presigned URL, 해시태그, 관심목록 |
| 5 | 5/1 (목) | Transaction 상태머신, ChatRoom (MySQL), Review, Block/Report |
| 6 | 5/2 (금) | Message (MongoDB), Notification (MongoDB), **WebSocket + STOMP** |
| 7 | 5/3 (토) | **Payment (토스 실연동)**, 충전, 거래 결제, 환불, 웹훅 |
| 8 | 5/4 (일) | Point 사용/출금, Withdrawal, Delivery (Mock) |
| 9 | 5/5 (월) | Notice, Banner, 관리자 페이지 (통계, 회원관리, 보증금반환, 신고처리) |
| 10 | 5/6 (화) | **통합 테스트, 버그 수정, 마무리, PM 연동 시작** |

### Codex 리뷰 게이트 (Day별)

> 게이트 정의는 `CLAUDE.md §9` / `AGENTS.md §6` 참조.

| Day | 작업 끝나는 시점 | 게이트 | 비고 |
|-----|-----------------|--------|------|
| 2 | SecurityConfig + JWT | 🔴 즉시 | 보안 핵심 |
| 3 | OAuth2Service (카카오/구글) | 🔴 즉시 | 토큰/사용자 매핑 |
| 4 | `feature/item-crud` PR 직전 | 🟡 PR 리뷰 | DDD-lite 구조 검증 |
| 5 | 거래 상태 머신 | 🔴 즉시 | 동시 거래 차단 결함 시 분쟁 |
| 6 | WebSocket 인증 | 🔴 즉시 | 인증 누락 시 모든 메시지 노출 |
| 7 | Payment (토스 실연동) | 🔴 즉시 + 🟡 PR 리뷰 | **이중** — 결제는 가장 중요 |
| 8 | Point / 출금 | 🔴 즉시 | 락/정합성 |
| 9 | 관리자 페이지 머지 | 🟡 PR 리뷰 | 권한 누락 점검 |
| 10 (5/6) | dev → main 전체 PR | 🟡 풀 리뷰 | 영역 간 일관성 최종 검증 |

### 위험 영역 (앞쪽에 배치)
- **Day 1~2**: 카카오/구글 OAuth 콘솔 등록 (외부 의존성)
- **Day 3~4**: OAuth2 연동 한 번 검증
- **Day 6**: WebSocket + Spring Security 통합 (인증 흐름 까다로움)
- **Day 7**: 토스페이먼츠 결제 한 건 끝까지 성공시키기

### 생존 룰
1. **Happy path 먼저**, 엣지 케이스는 `// TODO(5/6 이후):` 주석
2. **매일 저녁 30분 회고** + 일정 체크
3. **결제/포인트는 단위 테스트 필수**
4. Day 5(5/1)에 진행률 50% 미만이면 **WebSocket → 폴링 다운그레이드 검토**

---

## 9. Day 1 체크리스트 (4/27 오늘)

### 9.1 IntelliJ 프로젝트 생성
- [ ] [start.spring.io](https://start.spring.io) 또는 IntelliJ에서 Spring Initializr
- [ ] Project: **Gradle - Groovy**
- [ ] Language: **Java**
- [ ] Spring Boot: **3.3.x**
- [ ] Group: `com.sseulang`
- [ ] Artifact: `sseulang`
- [ ] Java: **21**
- [ ] Packaging: Jar
- [ ] Dependencies: Spring Web, Lombok, Validation (나머지는 build.gradle에서 직접 추가)

### 9.2 build.gradle 의존성 (전체)
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.sseulang'
version = '0.0.1-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    // Core
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'

    // Security + OAuth2
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly  'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly  'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // QueryDSL (Boot 3.x용)
    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api'

    // DB
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Migration
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'

    // S3
    implementation platform('software.amazon.awssdk:bom:2.27.21')
    implementation 'software.amazon.awssdk:s3'

    // Swagger
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:mysql'
    testImplementation 'org.testcontainers:mongodb'
    testImplementation 'org.testcontainers:junit-jupiter'
    testRuntimeOnly  'org.junit.platform:junit-platform-launcher'
}

test { useJUnitPlatform() }

// QueryDSL Q-class 생성 위치
def querydslSrcDir = 'build/generated/querydsl'
sourceSets.main.java.srcDir querydslSrcDir
tasks.withType(JavaCompile).configureEach {
    options.generatedSourceOutputDirectory = file(querydslSrcDir)
}
clean.doLast { file(querydslSrcDir).deleteDir() }
```

### 9.3 docker-compose.yml (로컬 개발용)
```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: sseulang-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: rootpw
      MYSQL_DATABASE: sseulang
      MYSQL_USER: sseulang
      MYSQL_PASSWORD: sseulangpw
      TZ: Asia/Seoul
    ports: ["3306:3306"]
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--default-time-zone=+09:00", "--ngram_token_size=2"]
    volumes: ["mysql-data:/var/lib/mysql"]

  redis:
    image: redis:7-alpine
    container_name: sseulang-redis
    restart: unless-stopped
    ports: ["6379:6379"]
    command: ["redis-server", "--appendonly", "yes"]
    volumes: ["redis-data:/data"]

  mongo:
    image: mongo:7
    container_name: sseulang-mongo
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: sseulang
      MONGO_INITDB_ROOT_PASSWORD: sseulangpw
      MONGO_INITDB_DATABASE: sseulang
      TZ: Asia/Seoul
    ports: ["27017:27017"]
    volumes: ["mongo-data:/data/db"]

volumes:
  mysql-data:
  redis-data:
  mongo-data:
```

### 9.4 application.yml (프로필 분리)
- `application.yml` — 공통
- `application-local.yml` — 로컬 (Docker Compose 연결)
- `application-prod.yml` — 운영 (EC2 환경변수 주입)

### 9.5 Day 1 체크리스트
- [ ] Spring Boot 3.3.5 + Java 21 프로젝트 생성
- [ ] build.gradle 의존성 전부 추가
- [ ] docker-compose.yml 작성, `docker compose up -d`로 띄우기 확인
- [ ] application.yml / application-local.yml 작성
- [ ] Flyway: `src/main/resources/db/migration/V1__init_schema.sql` (20개 테이블 DDL)
- [ ] `src/main/resources/db/migration/V2__seed_categories.sql` (카테고리 초기 데이터)
- [ ] **GlobalExceptionHandler + ApiResponse + ErrorCode + BusinessException + PageResponse**
- [ ] **TraceIdFilter (MDC + X-Trace-Id 응답 헤더)**
- [ ] **logback-spring.xml** (`%X{traceId}` 패턴 추가)
- [ ] **BaseEntity** (`@MappedSuperclass`, createdAt/updatedAt 자동 처리)
- [ ] **OpenApiConfig** (Swagger 설정, 쿠키 인증 표기)
- [ ] http://localhost:8080/swagger-ui.html 접속 확인
- [ ] GitHub repo 생성, 초기 커밋, Branch Protection (main: 직접 푸시 금지)
- [ ] GitHub Actions: build-only 워크플로 (`.github/workflows/build.yml`)
- [ ] **카카오 Developers 앱 등록** (REST API 키, 리다이렉트 URI)
- [ ] **Google Cloud Console OAuth 클라이언트 ID 발급**
- [ ] **AWS S3 버킷 생성** (`sseulang-bucket`, public-read 정책)
- [ ] **AWS IAM 사용자 생성** (S3 PutObject 권한, Access Key 발급)
- [ ] **토스페이먼츠 개발자센터 회원가입**, 테스트 키 발급
- [ ] PM에게 Swagger URL 공유 (배포 후) / 진행 상황 공유 채널 정하기

---

## 10. 다음 단계 (Day 2~)

### Day 2 시작 전 준비
- 외부 서비스 키 모두 `application-local.yml`에 환경변수로 주입 가능하게 설정
    - `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`
    - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
    - `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_REGION`, `S3_BUCKET`
    - `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`
    - `JWT_SECRET` (HS256, 64자 이상 랜덤)

### 진행 상황 추적
- 매일 저녁 이 문서 끝에 진행률 / 막힌 부분 기록
- 일정 차질 시 즉시 PM과 공유

---

## 11. TODO (5/6 이후 추가 가능 영역)

여유 생기면 다음을 고려:
- 검색: Elasticsearch 도입
- 자동완성, 오타 보정
- 알림 푸시 (FCM)
- 이미지 리사이징/썸네일 (Lambda 또는 프론트)
- CloudFront CDN
- 카카오페이 정식 연동 (사업자 등록 후)
- 통계 대시보드 고도화
- trust_score_histories 활용한 변동 이력 표시
- 실제 배달대행사(부릉/바로고) API 연동
- 단체 채팅 확장

---

## 12. 참고 — 결정 안 한 영역

PM과 추가 협의 필요한 부분:
- 사용자가 카카오/구글 외 다른 소셜로 가입 후 동일 이메일로 다른 provider 연결 가능한가?
- 배달대행 수수료 산정 공식
- 출금 최소 금액 / 출금 수수료
- 보증금 반환 분쟁 시 판단 기준
- 신고 누적 N회 → 자동 제재 룰

---

## 13. 트러블슈팅 노트 (Day별 누계)

> 작업 중 마주친 이슈 + 해결 + 교훈. 새 이슈 발생 시 본 섹션에 누적.

### 13.1 JPAQueryFactory 빈 미등록 (Day 4)
**증상**: ItemQuerydslRepository 작성 시 `NoSuchBeanDefinitionException: JPAQueryFactory`.
**원인**: `querydsl-apt` 는 Q-class 생성만, 빈 자동 등록 X.
**해결**: `global/config/QuerydslConfig.java` 에 `@Bean public JPAQueryFactory jpaQueryFactory()` 직접 등록.
**교훈**: 외부 라이브러리 빈은 명시적 Config 필요.

### 13.2 한국어 ENUM 매핑 (Day 4)
**증상**: DB ENUM `'대여','판매','나눔'` 과 Java enum 매핑 — 영문 enum + AttributeConverter는 보일러플레이트(6+ enum).
**해결**: 한국어 식별자 enum + `@Enumerated(EnumType.STRING)`. Java가 한국어 식별자 허용, `name()` 그대로 매칭.
```java
public enum TradeType { 대여, 판매, 나눔 }
```
**트레이드오프**: enum rename = 데이터 마이그(영문 enum 동일 비용). DDD Ubiquitous Language와 정합.

### 13.3 자식 엔티티 BaseEntity 상속 시 컬럼 mismatch (Day 4)
**증상**: ItemImage `extends BaseEntity` → `Schema-validation: missing column [updated_at]`.
**원인**: `item_images` 테이블엔 `created_at` 만 있고 `updated_at` 없음.
**해결**: BaseEntity 상속 X. `@CreatedDate` + `@EntityListeners(AuditingEntityListener.class)` 만.
**교훈**: 자식 엔티티는 테이블 정의(`updated_at` 유무) 먼저 확인.

### 13.4 @DataJpaTest + testcontainers MySQL (Day 4)
**증상**: `@DataJpaTest` 기본 H2 → 한국어 ENUM/ngram/FULLTEXT 미지원.
**해결**:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaAuditingConfig.class, ItemQuerydslRepository.class})
@Testcontainers
class XxxIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withCommand("--ngram_token_size=2", "--character-set-server=utf8mb4");
    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) { ... }
}
```
**추가 함정**:
- `@DataJpaTest` 는 `@EnableJpaAuditing` 자동 픽업 X → `@Import(JpaAuditingConfig.class)` 명시
- `items.seller_id` FK RESTRICT → 테스트 setUp에서 User 1건 먼저 persist 필요

### 13.5 Wishlist UNIQUE race — broad catch 위험 (Day 4, Codex 게이트 2)
**증상**: 첫 구현 `catch (DataIntegrityViolationException e) { /* 무시 */ }` — UNIQUE 외 FK 위반도 "성공" 처리.
**해결**: cause 좁은 검사
```java
if (cause instanceof ConstraintViolationException cve
        && "uk_wishlists_user_item".equalsIgnoreCase(cve.getConstraintName())) {
    return;
}
throw violation;
```
**교훈**: Day 3 OAuth race 처리 패턴 동일 — catch는 항상 의도한 케이스만 좁게.

### 13.6 wishlist_count 영구 stale (Day 4, Codex 게이트 2)
**증상**: Wishlist add/remove 가 `wishlists` 만 갱신, `items.wishlist_count` 0 고정.
**해결**: `@Modifying` SQL atomic update + Wishlist 도메인이 ItemApplicationService 경유 호출. delete 영향 행 1건일 때만 -1, `wishlist_count > 0` 가드로 음수 방지.
**교훈**: denormalized counter는 atomic update + 양방향 동기 필수.

### 13.7 IllegalStateException → 500 회귀 (Day 4, Codex 게이트 2)
**증상**: `Item.updateInfo` 거래완료/삭제 상태에서 IllegalStateException → GlobalExceptionHandler 500.
**해결**: 신규 `ErrorCode.ITEM_INVALID_STATE` (400) + 도메인이 `BusinessException` throw.
**교훈**: 단순 인자 검증은 `IllegalArgumentException`, 사용자 액션 컨텍스트의 상태 전이 거부는 `BusinessException` + 의미 있는 ErrorCode.

### 13.8 File presign 권한 누수 (Day 4, Codex 게이트 2)
**증상**: 일반 사용자가 `purpose=NOTICE`/`BANNER`/`MESSAGE` 로 관리자 자원 업로드 경로 선점 가능.
**해결**: `FileApplicationService.issueForUser` 화이트리스트 (`{PROFILE, ITEM}` 만), 그 외 FORBIDDEN. 도메인 내부용 `issue` 는 권한 검증 책임 호출자.
**교훈**: 사용자 입력 enum/discriminator는 항상 화이트리스트 검증, 진입점 분리로 권한 경계 명확히.

### 13.9 레이어 위반 — 다른 도메인 Repository 직접 호출 (Day 4, Codex 게이트 2)
**증상**: `ItemApplicationService → CategoryRepository`, `WishlistApplicationService → ItemRepository` 직접 호출. CLAUDE.md §3.3 위반.
**해결**: `CategoryApplicationService.requireExists`, `ItemApplicationService.requireActiveItem` / `incrementWishlistCount` / `decrementWishlistCount` 추가, 다른 도메인은 ApplicationService 경유.
**교훈**: 단순화 욕구로 컨벤션 우회 금지. 작업 시작 전 §3.3 룰 재확인.

### 13.10 Codex 풀 리뷰 응답 30분+ 지연 (Day 4)
**증상**: 첫 호출 60+ files / 3000+ insertions / 9 영역 prompt → 30분 응답 없음, 사용자 중단 (노트북 sleep 추정).
**대응**: 메모리(`feedback_codex_dual_setup.md`)에 "게이트 2 라도 영역 2~3개씩 분할 호출" 룰 추가.
**교훈**: prompt 사이즈 + 노트북 상태 양쪽 변수 고려.

### 13.11 cause chain wrapper 누락 — 견고성 보강 (Day 4, Codex 검증)
**증상**: `WishlistApplicationService` 의 isUniqueUserItemConflict 가 `violation.getCause()` 한 겹만 검사.
**위험**: 드물게 `DataIntegrityViolationException → JpaSystemException → ConstraintViolationException` 처럼 wrapper 가 한 겹 더 끼면 race 가 500 으로 잘못 떨어질 수 있음.
**해결**: cause chain traversal (자기참조 방어 포함) 으로 변경.
```java
Throwable cause = violation;
while (cause != null) {
    if (cause instanceof ConstraintViolationException cve
            && UNIQUE_USER_ITEM.equalsIgnoreCase(cve.getConstraintName())) {
        return true;
    }
    Throwable next = cause.getCause();
    if (next == cause) return false;
    cause = next;
}
```
**교훈**: 예외 처리에서 cause chain 은 끝까지 따라가는 게 안전. 자기참조 방어 필수.

### 13.12 Bulk update + persistence context 동기화 (Day 4, Codex 검증)
**증상**: `Item.incrementWishlistCount` / `decrementWishlistCount` 가 JPA `@Modifying @Query` bulk update.
**위험**: 같은 트랜잭션 안에서 후속으로 동일 Item 을 read 하면 stale 값 (persistence context 미동기).
**현 상태**: wishlist add/remove 흐름은 호출 직후 read 가 없어 안전. 단 향후 같은 트랜잭션에서 재읽기 흐름 도입 시 회귀 가능.
**해결**: 메서드에 stale 주의 javadoc 명시. 후속 `EntityManager.refresh` 또는 `flush+clear` 적용 hint.
**교훈**: bulk update 는 동기화 책임이 호출자 → 항상 문서화.

### 13.13 후속 이슈 트래킹 (Day 4 게이트 2 검증 권고)
**Issue #12**: Item 등록 후 presigned key 승격 (`items/{userId}/...` → `items/{itemId}/...`) — 가이드 §4.5 정합 + ownership 검증
**Issue #13**: Wishlist 동시성 IT — UNIQUE race + atomic counter underflow 검증 (testcontainers + CompletableFuture)
머지 차단 X (견고성 보강 영역). 5/6 이후 또는 보안/돈 영역 작업 시 함께 진행 권장.

---

## 14. 작업 흐름 학습 (Day별 누계)

- **TDD RED-GREEN-REFACTOR**: 도메인 단위는 테스트 우선, 어플리케이션은 권한·멱등성 케이스 우선
- **Mockless Fake 패턴**: Mockito 대신 InMemoryFake* 직접 작성. 테스트 가독성 ↑, 단 prod 의미 드리프트 위험 → IT로 보완
- **commit 분할**: 한 PR 내 의미 단위 분할 (Day 4 PR — 7 commit). 게이트 fix는 별도 commit으로 추적성 확보
- **컨벤션 일관성**: CLAUDE.md / AGENTS.md 룰을 작업 시작 전 다시 확인. 단순화 욕구로 우회 금지

---

**🚀 Day 1 시작! 막히면 이 문서로 돌아와서 결정 사항 다시 확인.**