# 프론트엔드 연동 가이드

> 쓸랭 백엔드 + 프론트엔드 연동을 위한 종합 가이드. 최신 갱신: 2026-05-14 (라운드 14 — 거래대행 대여 lifecycle + Toss webhook 시그니처 반영).

## 0. 빠른 시작

| 항목 | 로컬 | 프로덕션 |
|---|---|---|
| Base URL | `http://localhost:8080` | (배포 후 결정) |
| Swagger | `http://localhost:8080/swagger-ui.html` | (정책 결정 중) |
| WebSocket | `http://localhost:8080/ws-stomp` (SockJS) / `ws://localhost:8080/ws-stomp-native` (native) | 동일 |
| 응답 포맷 | `{success, data, error}` | 동일 |
| Time format | `LocalDateTime` (KST naive, 예: `2026-05-03T10:00:00`) — Message 만 `Instant` (UTC offset 포함) | 동일 |

---

## 1. CORS

백엔드 `app.cors.allowed-origins` 화이트리스트 매칭만 허용. 와일드카드(`*`) 미사용.

### 로컬
```yaml
# application-local.yml
app.cors.allowed-origins:
  - http://localhost:3000   # Next.js
  - http://localhost:5173   # Vite
```
프론트 dev 서버 포트가 다르면 PR 또는 환경변수로 추가 요청.

### 프로덕션
환경변수 `CORS_ALLOWED_ORIGINS` 콤마 구분:
```bash
CORS_ALLOWED_ORIGINS=https://sseulang.com,https://www.sseulang.com
```
- preflight (`OPTIONS`) 도 화이트리스트 통과 필수.
- 쿠키 동봉 호출은 클라이언트가 `credentials: 'include'` 또는 `axios.defaults.withCredentials = true` 설정.

### CORS 흔한 오류
| 증상 | 원인 |
|---|---|
| `No 'Access-Control-Allow-Origin' header` | 백엔드 화이트리스트에 origin 누락 |
| `credentials flag is true, ... must not be wildcard` | 백엔드는 `*` 안 씀 — origin 정확 매칭 필요 |
| `preflight ... 401` | OPTIONS 도 인증 거치는 endpoint — Spring Security 가 자동 통과시켜야 정상. 미통과 시 백엔드 이슈 |

---

## 2. 인증 흐름 (전체 그림)

> JWT는 **HttpOnly 쿠키로만 전달.** 클라이언트가 직접 토큰 보관·헤더 첨부 X.

### 2.1 회원가입/로그인 → AT 쿠키 + RT 쿠키 발급

```
POST /api/v1/auth/signup       (이메일/비밀번호) → 201 + 인증 메일 발송
POST /api/v1/auth/login        (이메일/비밀번호) → 200 + Set-Cookie: AT, RT + MeResponse
POST /api/v1/auth/oauth2/{provider}  (KAKAO|GOOGLE) → 200 + Set-Cookie + MeResponse
POST /api/v1/auth/refresh                          → 200 + 새 AT/RT (rotation) + MeResponse
```

**signup / login / oauth2 / refresh 응답 본문 (`MeResponse`)** — 페이지 새로고침 시 store 재초기화에 그대로 사용:
```json
{
  "success": true,
  "data": {
    "id": 42,
    "email": "alice@sseulang.test",
    "nickname": "쓸랭이",
    "profileImage": null,
    "socialProvider": "LOCAL",                // LOCAL | KAKAO | GOOGLE
    "emailVerified": false,                    // false 면 자금/거래/채팅/신고 API 가 403
    "pointBalance": 50000,
    "trustScore": 4.7,                         // null = 리뷰 0건 신규
    "reviewCount": 12
  }
}
```

응답 쿠키 (서버가 자동 설정):
- `access_token` — HttpOnly, Secure(prod), SameSite=Strict(prod)/Lax(local), Max-Age=1800
- `refresh_token` — HttpOnly, Secure(prod), SameSite=Strict(prod)/Lax(local), Max-Age=604800
- `XSRF-TOKEN` — HttpOnly **X** (JS 가 읽어 CSRF 헤더로 다시 보내야 함)

### 2.2 인증된 요청

```http
GET /api/v1/users/me HTTP/1.1
Cookie: access_token=...; XSRF-TOKEN=abc...
X-XSRF-TOKEN: abc...                    ← XSRF 쿠키 값을 헤더로 echo
```

- 모든 mutating 요청 (POST/PATCH/PUT/DELETE)에 `X-XSRF-TOKEN` 헤더 필수.
- GET 은 XSRF 헤더 X (Spring CSRF 정책).
- AT 쿠키는 브라우저가 자동 동봉 (`withCredentials: true`).

### 2.3 토큰 만료 → 자동 갱신

AT 만료 시 `401 AUTH_TOKEN_EXPIRED`:
```http
POST /api/v1/auth/refresh
Cookie: refresh_token=...
```
응답: 새 AT + RT 발급 (RT rotation) + `MeResponse`. 실패 시 `401 AUTH_REFRESH_TOKEN_INVALID` → 로그인 화면.

**axios interceptor 패턴**:
```js
axios.interceptors.response.use(
  res => res,
  async err => {
    if (err.response?.status === 401 && err.response?.data?.error?.code === 'AUTH_TOKEN_EXPIRED') {
      await axios.post('/api/v1/auth/refresh');
      return axios.request(err.config);  // 원래 요청 재시도
    }
    throw err;
  }
);
```

### 2.4 로그아웃

```http
POST /api/v1/auth/logout
```
- AT 즉시 blacklist + RT 폐기 + 쿠키 만료 (`Max-Age=0`).

### 2.5 본인 정보 조회 / 수정

```
GET   /api/v1/users/me                   → MeResponse
PATCH /api/v1/users/me                   → MeResponse (갱신된 값)
  Body: { profileImage?: string|"", nickname?: string }
  - null/생략 = 변경 X
  - "" (빈 문자열) = profileImage 제거
  - 이메일 미인증 사용자도 호출 가능 (자금/거래 영향 0)
```

### 2.6 다른 사용자 공개 프로필 조회

```
GET /api/v1/users/{id}/profile           → 비로그인 접근 가능
```
응답:
```json
{
  "id": 100,
  "nickname": "쓸랭이",
  "profileImage": null,
  "socialProvider": "LOCAL",
  "trustScore": 4.7,                  // null = 신규
  "reviewCount": 12,
  "createdAt": "2026-04-01T12:00:00"
}
```
**민감 정보 (이메일/잔액/emailVerified) 미노출.** ItemDetail 의 sellerId, ChatRoom 의 opponentId 등에서 호출.

### 2.7 이메일 인증 가드

`signup` 직후 사용자는 `emailVerified=false`. 다음 API 는 `403 AUTH_EMAIL_NOT_VERIFIED` 떨굼:
- 거래/결제/포인트 출금/배달대행 등 자금 영향
- 채팅방 개설 / 메시지 전송
- 신고
- 파일 presigned URL 발급 (단 `purpose=PROFILE` 은 우회 — 가입 직후 프로필 셋업)

→ 인증 메일 링크 클릭 → `POST /api/v1/auth/verify-email?token=...` 호출하면 `emailVerified=true`.

재발송: `POST /api/v1/auth/resend-verification` (인증 필수, rate-limited).

---

## 3. 응답 포맷

### 성공 (200/201)
```json
{ "success": true, "data": { ... } }
```

### 페이징
```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 123,
    "totalPages": 7,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 실패
```json
{
  "success": false,
  "error": {
    "code": "ITEM_NOT_FOUND",
    "message": "물품을 찾을 수 없습니다.",
    "traceId": "abc123..."
  }
}
```

`error.message` 는 한국어. 프론트에서 그대로 노출 OK. 단, 보안 민감 케이스는 통합 메시지(`AUTH_LOGIN_FAILED` 등) 사용.

### 시간 포맷 — 중요

| 도메인 | 타입 | 형식 | 비고 |
|---|---|---|---|
| 거의 전부 (Item, Transaction, ChatRoom, User, Payment, Withdrawal, ...) | `LocalDateTime` | `"2026-05-03T10:00:00"` | **TZ 정보 없음. KST 가정.** |
| Message (채팅 메시지) 만 | `Instant` | `"2026-05-03T10:00:00.123Z"` | UTC offset 포함 |

**프론트 처리 권장**: KST 강제 헬퍼 1개 만들어서 모든 timestamp 파싱 통과시키기 (해외 브라우저 케이스 방어).

```js
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import tz from 'dayjs/plugin/timezone';
dayjs.extend(utc); dayjs.extend(tz);

export const parseKst = (iso) => dayjs.tz(iso, 'Asia/Seoul');
```

---

## 4. 주요 ErrorCode 참조

> 전체 목록은 `src/main/java/com/sseulang/global/exception/ErrorCode.java`. 아래는 프론트가 분기해야 할 핵심.

### 인증/권한
| code | HTTP | 의미 | UX |
|---|---|---|---|
| `AUTH_LOGIN_FAILED` | 401 | 이메일/비밀번호 불일치 (이메일 존재 여부 노출 X) | "이메일 또는 비밀번호가 올바르지 않습니다" |
| `AUTH_TOKEN_MISSING` | 401 | AT 쿠키 없음 | 로그인 화면 |
| `AUTH_TOKEN_EXPIRED` | 401 | AT 만료 | refresh 자동 호출 |
| `AUTH_TOKEN_INVALID` | 401 | AT 위변조/포맷 오류 | 로그인 화면 |
| `AUTH_TOKEN_REVOKED` | 401 | 로그아웃된 AT 재사용 | 로그인 화면 |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | RT 만료/폐기 | 로그인 화면 |
| `AUTH_OAUTH_FAILED` | 401 | OAuth code 검증 실패 | 사용자 다시 로그인 유도 |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | 이메일 인증 필요 | 인증 메일 안내 모달 |
| `AUTH_VERIFICATION_TOKEN_INVALID` | 400 | 잘못된 인증 토큰 | "유효하지 않은 링크" |
| `AUTH_VERIFICATION_TOKEN_EXPIRED` | 400 | 만료된 인증 토큰 | resend 버튼 |
| `AUTH_VERIFICATION_RESEND_TOO_SOON` | 429 | rate limit | "잠시 후 다시 시도" |
| `AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER` | 409 | 같은 이메일 다른 SNS | "이미 X로 가입된 이메일" |
| `USER_BLOCKED` | 403 | 차단 계정 | 로그인 차단 화면 |

### Item / Transaction / Payment / Point
| code | HTTP | 의미 |
|---|---|---|
| `ITEM_NOT_FOUND` | 404 | 물품 없음 또는 삭제됨 |
| `ITEM_FORBIDDEN` | 403 | 본인 물품 아님 |
| `ITEM_INVALID_STATE` | 400 | 비공개/예약 등 현재 상태 불가 |
| `ITEM_IMAGE_LIMIT_EXCEEDED` | 400 | 이미지 10장 초과 |
| `ITEM_IMAGE_NOT_FOUND` | 404 | 이미지 부분 제거 시 미존재 url |
| `ITEM_IMAGE_ORDER_MISMATCH` | 400 | 순서 변경 입력이 기존 set 과 불일치 |
| `TRANSACTION_NOT_FOUND` | 404 | 거래 없음 |
| `TRANSACTION_FORBIDDEN` | 403 | 거래 참여자 아님 |
| `TRANSACTION_RESERVED_BY_OTHER` | 409 | 다른 사용자가 먼저 예약 |
| `TRANSACTION_INVALID_STATE` | 400 | 거래 상태 머신 위반 |
| `TRANSACTION_SELF_NOT_ALLOWED` | 400 | 본인 물품에 거래 시도 |
| `INSUFFICIENT_POINT` | 400 | 포인트 부족 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 토스 결제 금액 위변조 |
| `PAYMENT_DUPLICATED` | 409 | 같은 merchant_uid 재결제 |
| `WITHDRAWAL_IDEMPOTENCY_MISMATCH` | 409 | 같은 idempotencyKey 다른 내용 |
| `WITHDRAWAL_NOT_CANCELABLE` | 400 | 승인/완료 후 취소 시도 |
| `REVIEW_DUPLICATED` | 409 | 같은 거래 본인 리뷰 중복 |
| `REVIEW_PERIOD_EXPIRED` | 400 | 7일 지남 |

### Chat / Wishlist / Report
| code | HTTP | 의미 |
|---|---|---|
| `CHAT_ROOM_NOT_FOUND` | 404 | 채팅방 없음 |
| `CHAT_FORBIDDEN` | 403 | 채팅방 참여자 아님 / 본인 물품에 채팅 시도 |
| `CHAT_MESSAGE_EMPTY` | 400 | content 와 imageUrls 모두 비어있음 |

### 배달
| code | HTTP | 의미 |
|---|---|---|
| `DELIVERY_ALREADY_ACCEPTED` | 409 | 다른 라이더가 먼저 수락 |
| `DELIVERY_SELF_NOT_ALLOWED` | 400 | 본인 요청 수락 시도 |
| `DELIVERY_INVALID_STATE` | 400 | 상태 머신 위반 |

### 고객지원
| code | HTTP | 의미 |
|---|---|---|
| `INQUIRY_NOT_FOUND` | 404 | 1:1 문의 없음 |
| `INQUIRY_FORBIDDEN` | 403 | 본인 문의가 아님 |
| `INQUIRY_INVALID_STATE` | 400 | PENDING 아닌 문의 삭제 시도 / 답변 없이 DONE 변경 등 |
| `SUPPORT_POST_NOT_FOUND` | 404 | FAQ/QNA 게시글 없음 |

### 시스템
| code | HTTP | 의미 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 포맷 오류 (validation 실패) |
| `RESOURCE_NOT_FOUND` | 404 | 일반 리소스 없음 |
| `FORBIDDEN` | 403 | 일반 권한 거부 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 (traceId 로 백엔드 문의) |

---

## 5. OAuth 흐름 (카카오/구글) — Authorization Code Grant

> ⚠️ Token Grant 가 아니라 **Authorization Code Grant**. 프론트가 access_token 받아서 보내는 거 아님.

```
[Frontend]                       [Provider]                    [Backend]
   │                                 │                            │
   │── 로그인 버튼 클릭 ──────────────────►                       │
   │                                 │                            │
   │── Provider redirect URL 이동 (외부 redirect) ────►          │
   │      ?client_id=...&redirect_uri=...                          │
   │                                 │                            │
   │   (사용자 동의)                                              │
   │                                 │                            │
   │◄── /auth/{provider}/callback?code=XXX  (브라우저 redirect)──│
   │                                                                │
   │── POST /api/v1/auth/oauth2/{provider} ─────────────────►   │
   │   { code: "XXX", redirectUri: "https://app.../auth/{provider}/callback" }
   │                                                                │
   │                                  ── token endpoint ──────► provider
   │                                  ◄── access_token ───────  provider
   │                                  ── user info ─────────► provider
   │                                  ◄── id, email, ... ──── provider
   │                                                                │
   │◄── 200 + Set-Cookie: AT, RT + MeResponse ────────────────  │
```

### Body 형식
```json
POST /api/v1/auth/oauth2/kakao
{
  "code": "...",
  "redirectUri": "https://app.../auth/kakao/callback"  // 콘솔 등록된 URI 와 정확히 일치
}
```

### Provider 별 redirectUri
- 카카오: 콘솔 등록한 URI 와 **정확히** 일치 (대소문자/슬래시 포함)
- 구글: 동일

### 첫 가입 vs 재로그인
백엔드가 자동 분기 — `(provider, providerId)` 매핑 있으면 로그인, 없으면 신규 가입 (`emailVerified=true`, provider 가 검증한 이메일).

### LOCAL 가입 사용자가 OAuth 호출 시
같은 이메일이면 takeover (provider 마이그레이션 + verified=true). 자동.

### customerKey (토스 SDK)
일반 결제는 백엔드 customerKey 사용 X. 프론트 토스 SDK 가 호출할 때 `String(user.id)` 넘기면 OK (정기결제 미사용).

---

## 6. 결제 흐름 (토스페이먼츠)

```
[Frontend]                              [Backend]                  [Toss]
   │                                       │                         │
   │── POST /api/v1/payments/charge ──────►│                         │
   │   { amount }                          │                         │
   │◄── { paymentId, merchantUid, amount, tossClientKey } ──        │
   │                                                                  │
   │── Toss SDK 결제창 호출 ────────────────────────────────────►   │
   │   { merchantUid, amount, successUrl, failUrl, ... }              │
   │                                                                  │
   │◄── successUrl 로 redirect (paymentKey, orderId, amount) ────── │
   │                                                                  │
   │── POST /api/v1/payments/charge/confirm ─────────────────────►  │
   │   { paymentKey, orderId, amount }                                │
   │                                  ── confirm API ─────────────► │
   │                                  ◄── 검증 OK ──────────────── │
   │◄── 200 + 잔액 충전 완료 (PaymentResponse) ────────────────────│
```

### Endpoint 정확한 path
- `POST /api/v1/payments/charge` — 충전 시작 (백엔드가 merchantUid + tossClientKey 발급)
- `POST /api/v1/payments/charge/confirm` — 토스 redirect 후 확정
- `GET  /api/v1/payments/{id}` — 본인 결제 단건 조회
- `POST /api/v1/payments/webhook/toss` — 토스 webhook (프론트 호출 X)

### Toss webhook 처리 (프론트 호출 X)
토스가 직접 호출하는 서버 간 endpoint. 운영에서는 아래 헤더가 백엔드까지 보존돼야 한다.

| Header | 용도 |
|---|---|
| `tosspayments-webhook-transmission-id` | 멱등성 키. 중복 webhook 은 같은 event 로 처리 |
| `tosspayments-webhook-signature` | HMAC-SHA256 시그니처 |
| `tosspayments-webhook-timestamp` | replay window 검증용 timestamp |

백엔드는 시그니처 검증 후 payload 의 `paymentKey/orderId` 로 Toss lookup 을 다시 수행한다. lookup 실패/불일치면 `webhook_events.processed_at` 을 찍지 않고 다음 webhook/reconciliation 재시도를 기다린다.

### ChargeStartResponse 응답
```json
{
  "paymentId": 78,
  "merchantUid": "merchant-9f2c8c5b",   // 토스 결제창의 orderId 로 사용
  "amount": 50000,
  "tossClientKey": "test_ck_..."         // 프론트 SDK 초기화에 사용
}
```

### PaymentResponse (charge/confirm + GET 응답)
```json
{
  "id": 78,
  "userId": 100,
  "transactionId": null,           // 충전형은 항상 null. 거래 결제는 현재 미사용 (PointHistory 로 추적)
  "paymentType": "충전",            // 충전 | 거래 | 배달
  "method": "카드",                 // 카드 | 가상계좌 | 계좌이체 | 휴대폰 | 토스페이 등
  "amount": 50000,
  "status": "완료",                 // 대기 | 진행중 | 완료 | 실패 | 환불진행중 | 환불완료 | 환불실패
  "merchantUid": "merchant-9f2c8c5b",
  "paidAt": "2026-05-03T10:00:00", // status=완료 일 때만 채워짐
  "createdAt": "2026-05-03T09:59:50"
}
```

### 핵심 룰
- `merchantUid` 는 백엔드가 발급 (UNIQUE) — 프론트 임의 생성 금지.
- confirm 호출 시 `amount` 가 토스 응답과 mismatch → `PAYMENT_AMOUNT_MISMATCH` (위변조 차단).
- 같은 `merchantUid` 재호출 → `PAYMENT_DUPLICATED` (멱등성).
- 결제 페이지 자체는 토스 SDK가 렌더 — 별도 백엔드 페이지 X.
- webhook 은 프론트가 호출하지 않는다. 로컬 테스트에서 직접 호출할 때 운영 secret 이 설정돼 있으면 signature/timestamp 헤더가 필요하다.

### 결제 fail redirect 정책
백엔드는 `successUrl`/`failUrl` 받지 않음. 프론트 SDK 측 결정:
- success 라우트 → `POST /charge/confirm` 호출 → 잔액 갱신
- fail 라우트 → 백엔드 호출 X. 사용자에게 메시지만 표시
- 5분 reconciliation scheduler 가 자동으로 PENDING 정리

### 잔액 / 포인트 히스토리
- 잔액 자체는 `GET /api/v1/users/me` 의 `pointBalance` 사용 — 별도 endpoint X
- 변동 내역: `GET /api/v1/users/me/point/history?type=&page=&size=`
  - `type` (옵션): `충전 | 결제 | 판매정산 | 출금 | 환불 | 배달결제 | 배달정산`

---

## 7. 출금 (Withdrawal)

```
POST /api/v1/withdrawals
{
  "idempotencyKey": "uuid",        // 필수, ≤64. 같은 키 재호출은 멱등
  "amount": 30000,                  // ≥1
  "bankName": "신한",               // 자유 문자열 (enum 아님), ≤50
  "accountNumber": "110-...",
  "accountHolder": "홍길동"
}
→ 201 { success, data: <withdrawalId> }
```

추가 endpoint:
- `GET /api/v1/withdrawals?page=&size=` — 본인 출금 신청 목록
- `GET /api/v1/withdrawals/{id}` — 단건 (본인만)
- `DELETE /api/v1/withdrawals/{id}` — 신청 상태일 때만 취소 (자동 환불)

---

## 8. S3 파일 업로드 (presigned URL)

```
[Frontend]                              [Backend]                       [S3]
   │                                       │                              │
   │── POST /api/v1/files/presigned-url ──►│                              │
   │   { purpose: "ITEM",                  │                              │
   │     items: [{contentType, contentLength}] }                          │
   │◄── { uploads: [{presignedUrl, key}] } │                              │
   │                                                                       │
   │── PUT {presignedUrl} ─────────────────────────────────────────────► │
   │   body: <binary>                                                      │
   │   Content-Type: image/jpeg                                            │
   │◄── 200 ─────────────────────────────────────────────────────────── │
   │                                                                       │
   │── POST /api/v1/items                                                 │
   │   { ..., imageUrls: [<key 또는 GET URL>] }                           │
   │   백엔드가 items/{userId}/* → items/{itemId}/* 자동 promote           │
   │◄── 201 + itemId                                                      │
```

### purpose 값
| purpose | 폴더 | 용도 | 인증 필요 |
|---|---|---|---|
| `PROFILE` | `profiles/{userId}/...` | 본인 프로필 이미지 | 로그인만 (이메일 인증 우회) |
| `ITEM` | `items/{userId}/...` (임시) → `items/{itemId}/...` (등록 후) | 물품 이미지 | 이메일 인증 |
| `MESSAGE` | `messages/{userId}/...` | 채팅 첨부 이미지 | 이메일 인증 |
| `SUPPORT` | `support/{userId}/...` | 1:1 문의 첨부 이미지 | 이메일 인증 |
| `NOTICE` / `BANNER` | 도메인 전용 | 관리자 |

### 룰
- Content-Type: `image/jpeg|jpg|png|webp|gif` 만 허용.
- Content-Length: ≤ 5MB.
- 파일 한 번에 ≤ 10건.
- presigned URL 만료: 5분.
- Item: 응답의 `key` (또는 GET URL) 를 그대로 `imageUrls` 에 포함시키면 백엔드가 정식 폴더로 promote.
- Message: promote 단계 없음 — 등록 시점에 바로 final 폴더.

---

## 9. WebSocket / STOMP (실시간 채팅·알림)

### 9.1 SockJS 클라이언트 (브라우저, 쿠키 기반)

```js
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws-stomp'),
  // 쿠키 자동 동봉 — handshake 단계에서 SecurityContext 주입
  onConnect: () => {
    // 현재 보고 있는 채팅방 토픽 구독
    client.subscribe('/topic/chat-room/123', msg => {
      const message = JSON.parse(msg.body);   // MessageResponse
      // 메시지 화면에 추가
    });
    // 본인 알림 큐 구독 — 다른 방 새 메시지 / 시스템 알림
    client.subscribe('/user/queue/notifications', msg => {
      const noti = JSON.parse(msg.body);
      // 알림 리스트 / 토스트 표시
    });
  },
});
client.activate();
```

### 9.2 Native WebSocket 클라이언트 (모바일, 쿠키 X)

```js
const client = new Client({
  brokerURL: 'ws://localhost:8080/ws-stomp-native',
  connectHeaders: {
    Authorization: 'Bearer <jwt-access-token>',
  },
  onConnect: () => { ... },
});
client.activate();
```
- AT 만료 시 SECURITY 에러 → refresh 후 재연결.

### 9.3 메시지 전송 — REST 만 사용

⚠️ **주의: STOMP `/app/chat/send` destination 은 백엔드에 미구현.** 메시지 전송은 **REST 만**:

```
POST /api/v1/chat-rooms/{roomId}/messages
Body: {
  "content": "안녕하세요",        // content / imageUrls 둘 중 하나 이상 필수
  "imageUrls": []                  // 또는 ["https://cdn.../messages/200/abc.jpg"]
}
→ 201 + MessageResponse
```

전송 후 백엔드가 자동으로:
1. MongoDB 에 메시지 저장
2. ChatRoom 메타 갱신 (last_message, 상대 unread +1)
3. STOMP `/topic/chat-room/{roomId}` 로 broadcast — 양쪽 참여자 모두 수신
4. 상대방 `/user/queue/notifications` 로 알림 push

### 9.4 destination 화이트리스트

| destination | 방향 | 용도 | 권한 |
|---|---|---|---|
| `/topic/chat-room/{roomId}` | 백엔드→클라 | 채팅방 메시지 broadcast | 참여자만 SUBSCRIBE 가능 |
| `/user/queue/notifications` | 백엔드→클라 (본인) | 알림 (다른 방 새 메시지, 시스템) | 본인만 |

⚠️ **사용 안 하는 destination** (이전 doc 잘못 기재):
- ~~`/app/chat/send`~~ — 백엔드 미구현. 메시지 전송은 REST `POST /chat-rooms/{id}/messages` 만.
- ~~`/user/queue/messages`~~ — 백엔드 발행 안 함. 무시.

그 외 destination subscribe → `FORBIDDEN`.

---

## 10. 도메인 schema 정리 (응답 본문)

### 10.1 Item

#### ItemSummaryResponse — 검색·찜·내물품·내목록 공통
```json
{
  "id": 42,
  "sellerId": 100,
  "categoryId": 5,
  "title": "아이폰 14 Pro 미개봉",
  "price": 1200000,
  "tradeType": "판매",                  // 판매 | 대여 | 나눔
  "status": "판매중",                    // 판매중 | 예약 | 거래완료 | 비공개 | 삭제
  "region": "서울 강남구",
  "thumbnailUrl": "https://cdn.../items/42/abc.jpg",  // null 가능
  "rentalActive": false,                 // 현재 대여 진행 중이면 true. true 면 프론트에서 "대여중" 태그 표시
  "wishlistCount": 8,
  "isWishlisted": false,                 // 본인 찜 여부. 비로그인 항상 false
  "createdAt": "2026-05-03T10:00:00"
}
```

#### ItemDetailResponse
Summary + 추가 필드:
```json
{
  // Summary 모든 필드 +
  "description": "박스 미개봉, 색상 딥퍼플",
  "deposit": 100000,                    // 대여만, 그 외 null
  "rentalUnit": "일",                    // 대여만 ("시간"|"일"|"주"|"월"), 그 외 null
  "rentalActive": false,                // 현재 대여 진행 중이면 true
  "viewCount": 127,
  "images": [
    { "imageUrl": "...", "sortOrder": 1, "thumbnail": true }
  ],
  "hashtags": ["아이폰","미개봉"],
  "updatedAt": "2026-05-03T10:00:00"
}
```

#### Item endpoints
```
POST   /api/v1/items                           — 등록 (이메일 인증)
GET    /api/v1/items?q=&categoryId=&tradeType=&minPrice=&maxPrice=&tag=&sort=&page=&size=
                                                — 검색 (공개)
                                                  sort: latest | price_asc | price_desc | view_desc | wishlist_desc
GET    /api/v1/items/{id}                      — 단건 (공개, viewCount +1)
PATCH  /api/v1/items/{id}                      — 본인 수정 (전체 교체 패턴)
DELETE /api/v1/items/{id}                      — 본인 soft delete

POST   /api/v1/items/{id}/images               — 이미지 부분 추가 (10장 한도)
DELETE /api/v1/items/{id}/images?imageUrl=...  — 이미지 단건 제거
PATCH  /api/v1/items/{id}/images/order         — 순서 변경 (set 일치 검증)

GET    /api/v1/users/me/items?status=&page=&size=
                                                — 본인 등록 물품 (마이페이지)
                                                  status: 판매중|예약|거래완료|비공개
```

#### 등록/수정 body (ItemRegisterRequest / ItemUpdateRequest)
```json
{
  "categoryId": 5,                       // optional
  "title": "아이폰 14 Pro 미개봉",       // 필수 ≤200
  "description": "박스 미개봉...",        // 필수
  "price": 1200000,                      // 필수 ≥0 (나눔이면 0)
  "tradeType": "판매",                    // 필수, 등록 시만 (수정 불가)
  "deposit": null,                        // 대여만 필수, 그 외 null
  "rentalUnit": null,                     // 대여만 필수 ("시간"|"일"|"주"|"월")
  "region": "서울 강남구",                // optional ≤100
  "imageUrls": ["https://..."],           // optional, 최대 10장
  "hashtags": ["아이폰"]                  // optional
}
```
- **PATCH 의 imageUrls non-null = 전체 교체** (4장 유지하려면 4장 다시 보내야 함). 부분 편집은 분리 endpoint 사용.
- `tradeTypes` 는 상품의 지원 모드이고, `rentalActive` 는 현재 대여 진행 여부입니다. 프론트의 `대여중` 배지는 `rentalActive=true` 일 때만 표시해야 합니다.

### 10.2 Wishlist

```
POST   /api/v1/items/{id}/wishlist     → { wishlisted: true,  wishlistCount: 9 }
DELETE /api/v1/items/{id}/wishlist     → { wishlisted: false, wishlistCount: 8 }
GET    /api/v1/users/me/wishlist?page=&size=    → Page<ItemSummaryResponse>
```
- 토글 응답 즉시 반영 — detail 재조회 X.
- 멱등 (이미 찜한 상태 POST OK, 안 찜한 상태 DELETE OK).

### 10.3 Transaction

#### 상태 머신
```
판매/나눔:
채팅중 → 예약 → 인계완료 → 거래완료
   └─→ 취소 (채팅중/예약 양쪽에서 가능)

대여:
채팅중 → 예약 → 인계완료 → 반납요청 → 거래완료
   └─→ 취소 (채팅중/예약 양쪽에서 가능)
```
- 상태값은 모두 **한글**: `채팅중 | 예약 | 인계완료 | 반납요청 | 거래완료 | 취소`. "진행중" 없음.
- 직접 거래는 사이트 포인트 정산을 하지 않는다. 결제/정산은 오프라인 또는 별도 외부 수단이며, 백엔드는 상태와 Item 잠금만 관리한다.

#### TransactionResponse
```json
{
  "id": 12,
  "itemId": 42,
  "sellerId": 100,
  "buyerId": 200,
  "tradeType": "판매",
  "price": 1200000,
  "deposit": null,                        // 대여만
  "rentalStart": null,                    // 대여만
  "rentalEnd": null,                      // 대여만
  "status": "채팅중",
  "reservedAt": null,
  "completedAt": null,
  "canceledAt": null,
  "cancelReason": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

#### endpoints
```
POST  /api/v1/transactions                    → 201 + { id }
  Body: { itemId, rentalStart?, rentalEnd? }   (대여는 rentalStart/End 필수)
  - 본인 물품 거부 (TRANSACTION_SELF_NOT_ALLOWED)
  - 생성 즉시 status=채팅중. Item.status 는 변화 X (예약 액션 호출 시점에 잠김)

GET   /api/v1/transactions/{id}               → TransactionResponse (참여자만)

PATCH /api/v1/transactions/{id}               → null
  Body: { action: "예약"|"인계확인"|"인수확인"|"완료"|"반납요청"|"회신확인"|"취소", cancelReason?: string }
  - 예약: seller, 채팅중→예약, Item 자동 → 예약
  - 인계확인: seller, 예약→인계완료
  - 인수확인: buyer, 인계완료→거래완료, 판매/나눔만. 대여는 INVALID_STATE
  - 완료: seller, 채팅중/예약/인계완료→거래완료, 판매/나눔만. 대여는 INVALID_STATE
  - 반납요청: buyer, 대여만 인계완료→반납요청
  - 회신확인: seller, 대여만 반납요청→거래완료
  - 취소: 양쪽 참여자, Item 예약이었으면 → 판매중 복원

GET   /api/v1/users/me/transactions?role=buyer|seller&status=&page=&size=
  - role 미지정 = 양쪽. status 미지정 = 전체
```

⚠️ **직접 거래는 포인트/PG 정산 없음**. `PaymentResponse.transactionId` 는 항상 null. 거래대행 정산 추적은 `PointHistory` 에 기록된다.

⚠️ **직접 대여와 거래대행 대여는 endpoint 가 다르다.** 직접 대여는 `PATCH /transactions/{id}` 의 `반납요청/회신확인` action 을 사용한다. 거래대행 대여는 아래 Escrow 전용 endpoint 를 사용한다.

### 10.4 Escrow / 거래대행

#### 상태 머신
```
공통:
정보입력대기 → 결제대기 → 결제완료 → 진행중
                               ├─ 판매/나눔: buyer confirmReceipt → 완료
                               └─ 대여: buyer confirmReceipt → 사용중

대여 거래대행:
사용중 → 반납중 → 완료
   │        │
   │        └─ seller confirmReturn: 판매자 정산 + 보증금 환불 + forward/return 라이더 정산 + paired Transaction 생성
   └─ rentalEndAt 경과 시 scheduler 가 자동 requestReturn

사용중 취소:
사용중 → cancel-request → 사용중(취소 대기) → cancel-confirm → 취소
                    └─ cancel-withdraw → 사용중
```

- 상태값: `정보입력대기 | 결제대기 | 결제완료 | 진행중 | 사용중 | 반납중 | 완료 | 취소`
- 판매자/구매자 share 결제는 포인트 잔액에서 차감된다. 보증금은 대여 buyer 결제 시 `point_hold` 로 잡고 `confirmReturn` 또는 사용중 취소 확정 시 반환한다.
- 대여 `confirmReceipt` 는 정산하지 않고 `사용중` 으로 진입한다. 판매자 `itemPrice` 정산은 `confirmReturn` 시점에 일어난다.
- 수동/자동 반납 요청 모두 return delivery fee 를 buyer 포인트에서 추가 차감한 뒤 return delivery 를 모집한다.

#### 핵심 endpoints
```
POST /api/v1/escrow/applications/internal
  - 판매자가 채팅방에서 거래대행 생성. 대여 item 이면 rentalEndAt 필수

POST /api/v1/escrow/applications/internal/draft
  - 판매자가 본인 영역만 입력. 대여 item 이면 rentalEndAt 필수

PATCH /api/v1/escrow/applications/{id}/buyer-info
  - 구매자 영역 입력. 양쪽 입력 완료 시 fee 산정 + 결제대기 전환

GET  /api/v1/escrow/applications/{id}/payment-preview
POST /api/v1/escrow/applications/{id}/pay
  - 본인 share 결제. 양쪽 완료 시 결제완료 + forward delivery 모집

POST /api/v1/escrow/applications/{id}/confirm-receipt
  - buyer 수령 확인. 판매/나눔은 완료 정산, 대여는 사용중 진입

POST /api/v1/escrow/applications/{id}/request-return
  - 대여 buyer 수동 반납 요청. 사용중→반납중 + return fee 차감 + return delivery 모집

POST /api/v1/escrow/applications/{id}/confirm-return
  - 대여 seller 회신 확인. 반납중→완료 + seller/rider 정산 + 보증금 환불

POST /api/v1/escrow/applications/{id}/cancel-request
POST /api/v1/escrow/applications/{id}/cancel-confirm
POST /api/v1/escrow/applications/{id}/cancel-withdraw
  - 대여 사용중 단계 합의 취소
```

#### 내부 생성 body 추가 필드
```json
{
  "chatRoomId": 7,
  "itemId": 123,
  "tradeMode": "INTERNAL",
  "feePayer": "both",
  "itemPrice": 30000,
  "rentalEndAt": "2026-05-20T18:00:00"
}
```
- `rentalEndAt` 은 대여 거래대행에서 필수. 이 시각이 지나도 buyer 가 반납 요청을 누르지 않으면 스케줄러가 자동으로 `request-return` 과 동일한 효과를 수행한다.

### 10.5 ChatRoom

#### ChatRoomResponse
```json
{
  "id": 8,
  "itemId": 42,
  // viewer 기준 — 프론트는 이것만 보면 됨
  "opponentId": 200,
  "opponentNickname": "쓸랭이",
  "opponentProfileImage": "https://...",   // null 가능
  "myUnread": 3,
  "itemTitle": "아이폰 14 Pro",
  "itemThumbnailUrl": "https://...",       // null 가능
  // 메타
  "lastMessage": "안녕하세요...",
  "lastMessageAt": "2026-05-03T10:00:00",
  "active": true,
  // raw — 호환용 (향후 v2 에서 제거 예정. 신규 코드는 위 derived 필드 사용)
  "user1Id": 100,
  "user2Id": 200,
  "user1Unread": 0,
  "user2Unread": 3,
  "createdAt": "...",
  "updatedAt": "..."
}
```

#### endpoints
```
POST  /api/v1/chat-rooms                        → ChatRoomResponse (멱등)
  Body: { itemId }
  - 같은 (요청자, 상대, 물품) 채팅방이 있으면 기존 반환
  - 본인 물품 거부 (CHAT_FORBIDDEN)
  - 이메일 인증 필수

GET   /api/v1/chat-rooms?page=&size=             → Page<ChatRoomResponse>
  - lastMessageAt DESC

GET   /api/v1/chat-rooms/{id}                    → ChatRoomResponse (참여자만)

PATCH /api/v1/chat-rooms/{id}/read               → ChatRoomResponse (myUnread=0 반영됨)
  - 본인 unread 만 0 으로 atomic UPDATE
  - 채팅방 진입 시 / 새 메시지 수신 후 호출
```

### 10.6 Message

#### MessageResponse
```json
{
  "id": "65a1b2c3d4e5f60001234567",         // ⚠️ String (MongoDB ObjectId hex)
  "chatRoomId": 8,                            // ⚠️ "roomId" 아님
  "senderId": 200,
  "content": "안녕하세요",                     // null 가능 (이미지만 보낼 때)
  "imageUrls": ["https://cdn.../messages/200/abc.jpg"],   // ⚠️ 배열, 단수 아님
  "createdAt": "2026-05-03T10:00:00.123Z"     // ⚠️ Instant — UTC offset 포함
}
```
- `id` 는 MongoDB ObjectId hex string. 커서 페이징의 `before=` 값으로 그대로 사용.
- `content` 또는 `imageUrls` 둘 중 하나 이상 채워짐.
- 발신자 닉네임/프로필 미포함 → ChatRoom 의 user1/user2 + opponent 정보로 매칭하거나 `/users/{senderId}/profile` 호출.

#### endpoints
```
POST  /api/v1/chat-rooms/{roomId}/messages       → 201 + MessageResponse (broadcast 자동)
  Body: { content?: string, imageUrls?: string[] }   (둘 중 하나 이상)

GET   /api/v1/chat-rooms/{roomId}/messages?before=&size=30
                                                  → List<MessageResponse> (참여자만, 최신순)
  - before 생략 = 최신부터
  - before = 메시지 id (MongoDB ObjectId hex string) → 그 이전 size 개
```

### 10.7 Report (신고)

```
POST /api/v1/items/{itemId}/report             → 201 + { id } (이메일 인증)
POST /api/v1/users/{userId}/report             → 201 + { id } (이메일 인증)

Body:
{
  "reason": "사기 의심",                      // 필수 ≤50
  "detail": "결제 후 연락 끊김..."             // optional ≤5000
}
```

### 10.8 Category

```
GET /api/v1/categories                         → 활성 카테고리 트리 (root → children 재귀)
GET /api/v1/categories/{id}                    → 단건
```
- DB 시드된 트리 (2단 깊이). enum 고정 X.

### 10.9 Banner (메인 배너, 공개)

```
GET /api/v1/banners                            → List<BannerResponse>
```
- 활성(`active=true`) + 노출 윈도우(`startsAt~endsAt`) 통과한 배너만. `sortOrder` 오름차순.
- 인증 불필요.

#### BannerResponse
```json
{
  "id": 2,
  "adminId": 1,
  "title": "5월 봄맞이 이벤트",
  "imageUrl": "https://cdn.sseulang.com/banners/1/spring.jpg",
  "linkUrl": "/events/spring",
  "sortOrder": 1,
  "active": true,
  "startsAt": "2026-05-01T00:00:00",
  "endsAt": "2026-05-31T23:59:59",
  "createdAt": "2026-04-30T12:00:00"
}
```

### 10.10 Notice (공지, 공개)

```
GET /api/v1/notices?type=&page=&size=          → Page<NoticeResponse>
GET /api/v1/notices/{id}                       → NoticeResponse (viewCount +1)
```
- `type` 필터 옵션 (`NoticeType` enum). 미지정 시 전체.
- `published=true` + 노출 윈도우 통과 + `pinned` 우선 정렬.
- 미공개/윈도우 외 단건 조회 시 404 `NOTICE_NOT_FOUND`.

#### NoticeResponse
```json
{
  "id": 3,
  "adminId": 1,
  "type": "공지",                               // NoticeType enum 한글값
  "title": "결제 시스템 점검 안내",
  "content": "5/3 03:00~05:00 결제 일시 중단됩니다.",
  "imageUrl": null,
  "pinned": true,
  "published": true,
  "viewCount": 152,
  "startsAt": "2026-05-01T00:00:00",
  "endsAt": "2026-05-10T00:00:00",
  "createdAt": "2026-04-30T12:00:00"
}
```

### 10.11 Notification (본인 알림)

```
GET   /api/v1/notifications?page=&size=        → Page<NotificationResponse>
PATCH /api/v1/notifications/{id}/read          → null  (id 는 String — MongoDB ObjectId hex 24자)
```
- 본인 알림만 페이징 (createdAt DESC).
- `read=false` 필터 서버 미지원 (현재 클라이언트 책임 — follow-up).
- 다른 사용자 알림 / 미존재 id 의 markRead 시도 시 **silently 무시** (예외 X) — 정보 노출 차단 + 멱등.

#### NotificationResponse
```json
{
  "id": "65a1b2c3d4e5f60001234567",   // ⚠️ String (MongoDB ObjectId hex), Long 아님
  "type": "CHAT",                       // NotificationType enum (영문)
  "title": "새 메시지",
  "content": "안녕하세요, 거래 가능할까요?",
  "linkType": "CHAT_ROOM",              // CHAT_ROOM | TRANSACTION | DELIVERY 등 — 클라이언트 라우팅 키
  "linkId": 8,                          // linkType 의 row id
  "read": false,
  "createdAt": "2026-05-03T10:00:00.123Z"   // ⚠️ Instant (UTC offset 포함)
}
```

#### STOMP 푸시 (실시간)
- 신규 알림 발생 시 `/user/queue/notifications` 로 자동 broadcast (§9 참조).
- 클라이언트는 STOMP 구독 + REST 페이징 둘 다 사용 (재접속/페이지 새로고침 시 REST 로 보강).

### 10.12 UserBlock (사용자 차단)

```
POST   /api/v1/blocks                          → null   (Body: { userId })
DELETE /api/v1/blocks/{userId}                 → null
GET    /api/v1/blocks?page=&size=              → Page<UserBlockResponse>
```
- 차단 후 양쪽 모두 채팅/거래 불가.
- 차단/해제 모두 멱등.

#### UserBlockResponse
```json
{
  "id": 23,
  "blockerId": 100,         // 본인
  "blockedId": 200,         // 차단당한 사용자
  "createdAt": "2026-05-03T10:00:00"
}
```

### 10.13 Review (거래 후기)

#### endpoints
```
POST  /api/v1/reviews                          → 201 + { id }
  Body: { transactionId, rating: 1~5, comment?: ≤500 }
  - 거래완료 후 7일 이내 + 거래 참여자만
  - 같은 거래 본인 리뷰 중복 → 409 REVIEW_DUPLICATED
  - 작성 후 reviewee.trustScore 자동 재계산 (atomic)

GET   /api/v1/users/{userId}/reviews?page=&size=   → Page<ReviewResponse>
  - userId 가 reviewee 인 리뷰 페이징
  - ⚠️ comment 는 **작성자 본인 조회 시에만** 채워짐. 그 외엔 null 마스킹

GET   /api/v1/reviews/pending?page=&size=      → Page<PendingReviewResponse>
  - 본인이 reviewer 로 아직 작성 안 한 7일 이내 완료 거래
  - deadline 필드로 카운트다운 UI 가능
```

#### ReviewResponse
```json
{
  "id": 9,
  "transactionId": 12,
  "reviewerId": 200,
  "revieweeId": 100,
  "rating": 5,                       // 1~5
  "comment": "친절하고 빠른 거래",     // ⚠️ 작성자 본인 조회시에만, 타인은 null 마스킹
  "createdAt": "2026-05-03T10:00:00"
}
```

#### PendingReviewResponse
```json
{
  "transactionId": 12,
  "itemId": 42,
  "revieweeId": 200,                          // 상대방 (reviewer 의 카운터파트)
  "tradeType": "판매",
  "price": 1200000,
  "completedAt": "2026-05-01T14:00:00",
  "deadline": "2026-05-08T14:00:00"           // 완료 + 7일
}
```

### 10.14 Delivery (배달대행)

#### 상태 머신
```
모집중 → 수락 → 배송중 → 배송완료 → 정산완료
   └→ 취소 (요청자, 모집중 한정)
```
- 상태값 모두 **한글**: `모집중 | 수락 | 배송중 | 배송완료 | 정산완료 | 취소`
- 수락 이후 취소 → 400 `DELIVERY_INVALID_STATE` (분쟁 흐름 follow-up)

#### Item / Transaction 과의 관계 — **❌ 없음. 완전 독립 도메인**
- DeliveryRequest 엔티티에 itemId / transactionId FK 없음
- `itemDescription` 은 자유 텍스트 String (예: "A4 서류 봉투 1개")
- 배달 등록 시 거래 자동 생성 X — 별개 흐름
- 수수료(fee)는 정산(complete) 시점에 잔액에서 이동 (PG 안 거침), `PointReferenceType.DELIVERY`

#### DeliveryCreateRequest — 등록 body
```json
POST /api/v1/deliveries
{
  "pickupAddress":     "서울 강남구 테헤란로 123",   // 필수 ≤255
  "dropoffAddress":    "서울 송파구 올림픽로 456",   // 필수 ≤255
  "itemDescription":   "A4 서류 봉투 1개",           // 필수 ≤255 (자유 텍스트)
  "fee":               5000,                         // 필수 > 0 (라이더 수수료, 원)
  "requestedDeadline": "2026-05-04T15:00:00",        // optional, 미래 시각만
  "memo":              "1층 로비 보관함"             // optional ≤500
}
```

#### DeliveryResponse — 모든 GET / PATCH 응답
```json
{
  "id": 55,
  "requesterId": 100,
  "riderId": null,                                   // 수락 후 채워짐
  "pickupAddress": "서울 강남구 테헤란로 123",
  "dropoffAddress": "서울 송파구 올림픽로 456",
  "itemDescription": "A4 서류 봉투 1개",
  "fee": 5000,
  "requestedDeadline": "2026-05-04T15:00:00",        // null 가능
  "memo": "1층 로비 보관함",                          // null 가능
  "status": "모집중",
  "requestedAt": "2026-05-03T10:00:00",
  "acceptedAt": null,
  "pickedUpAt": null,
  "deliveredAt": null,
  "completedAt": null,
  "canceledAt": null,
  "cancelReason": null
}
```

#### endpoints — 액션마다 분리 endpoint (Transaction 의 단일 PATCH 와 패턴 다름)

| Method | Path | 호출자 | 상태 전이 | 비고 |
|---|---|---|---|---|
| POST | `/api/v1/deliveries` | requester | → 모집중 | 이메일 인증. 등록 시 fee 차감 X (정산에서) |
| GET | `/api/v1/deliveries` | 누구나 | — | 모집중 페이징 (라이더 후보 화면) |
| GET | `/api/v1/deliveries/me` | 본인 | — | requester/rider 로 참여한 목록 |
| GET | `/api/v1/deliveries/{id}` | 모집중=누구나 / 그 외=참여자 | — | 단건 |
| PATCH | `/api/v1/deliveries/{id}/accept` | rider | 모집중 → 수락 | 본인 등록 거절(400 DELIVERY_SELF_NOT_ALLOWED). 동시 race → 한 명만(409 DELIVERY_ALREADY_ACCEPTED) |
| PATCH | `/api/v1/deliveries/{id}/pickup` | 수락 rider | 수락 → 배송중 | |
| PATCH | `/api/v1/deliveries/{id}/deliver` | 수락 rider | 배송중 → 배송완료 | |
| PATCH | `/api/v1/deliveries/{id}/complete` | requester | 배송완료 → 정산완료 | requester 차감 → rider 적립 (id-asc 락). 잔액 부족 → 400 INSUFFICIENT_POINT + 전체 롤백 |
| PATCH | `/api/v1/deliveries/{id}/cancel` | requester | 모집중 → 취소 | body `{ reason? }`. 수락 이후 취소 시 400 |

배달 응답(`GET /api/v1/deliveries/{id}`, `GET /api/v1/deliveries`, `GET /api/v1/deliveries/me`) 에는 `requesterNickname` 과 `riderNickname` 이 포함됩니다. 프론트는 시연용 더미 라이더를 한글 닉네임으로 그대로 표시할 수 있습니다.
운영 환경에서 `app.delivery.auto-accept-rider-id` 를 설정하면 거래대행 결제 완료 후 배달이 자동 생성되고, 더미 라이더가 즉시 수락되어 `수락` 상태부터 위치 추적이 가능합니다.

#### 실시간 위치 추적 — STOMP + REST fallback (closed #51)

**Publish (라이더 → 백엔드)**
```
destination: /app/delivery/{deliveryId}/location
payload    : { latitude, longitude, accuracyM?, recordedAt? }
```
- 라이더 본인만 publish (그 외 `DELIVERY_FORBIDDEN`)
- 추적 가능 상태(`수락` / `배송중`) 에서만. 그 외 상태 → `DELIVERY_INVALID_STATE`
- 같은 `(deliveryId, riderId)` 최소 publish 간격 **1초** — 초과 시 `DELIVERY_LOCATION_TOO_FREQUENT` (429). 권장 주기 **5초**
- 좌표 한국 범위(33~39N / 124~132E) 강제. 벗어나면 `DELIVERY_LOCATION_INVALID`
- `recordedAt` drift 서버 시각 ±60초만 허용 — 초과 시 서버 시각으로 자동 정정

**Subscribe (요청자/라이더)**
```
destination: /topic/delivery/{deliveryId}/location
```
- 참여자(요청자/라이더) + 추적 가능 상태에서만 SUBSCRIBE 통과. 그 외 `FORBIDDEN`
- payload: 위와 동일 `DeliveryLocation` JSON

**REST fallback (재연결/최초 진입 — 마지막 좌표 즉시 표시)**
```
GET /api/v1/deliveries/{id}/location/last
  → 200 + DeliveryLocation
  → 204 No Content (캐시 미존재 / TTL 만료 / 종료 상태)
```

**저장 정책**
- DB 저장 X. Redis 휘발 캐시 (TTL 30분, 마지막 1건만)
- 정산완료(`complete`) / 취소(`cancel`) 시 즉시 evict — 종료 후 위치 잔류 차단

#### 활용 예시
```jsx
// 라이더
await api.patch(`/deliveries/${id}/accept`);   // → 수락
await api.patch(`/deliveries/${id}/pickup`);   // → 배송중

// 라이더 — 좌표 publish (5초 주기 권장)
const stomp = ...;
setInterval(() => {
  navigator.geolocation.getCurrentPosition(pos => {
    stomp.publish({
      destination: `/app/delivery/${id}/location`,
      body: JSON.stringify({
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
        accuracyM: pos.coords.accuracy,
        recordedAt: new Date().toISOString()
      })
    });
  });
}, 5000);

await api.patch(`/deliveries/${id}/deliver`);  // → 배송완료

// 요청자
await api.post('/deliveries', { pickupAddress, dropoffAddress, itemDescription, fee });

// 요청자 — 라이더 위치 구독 + REST fallback 으로 초기 표시
const last = await api.get(`/deliveries/${id}/location/last`);  // 204 면 데이터 없음
if (last.status === 200) renderMarker(last.data);
stomp.subscribe(`/topic/delivery/${id}/location`, msg => renderMarker(JSON.parse(msg.body)));

await api.patch(`/deliveries/${id}/complete`); // → 정산완료, 잔액 이동, 위치 캐시 evict
```

### 10.15 Inquiry (1:1 문의 — 본인)

비공개 1:1 문의. 본인이 작성·삭제, 본인만 조회. 관리자가 답변. 첨부 이미지 최대 5장 (presigned `purpose=SUPPORT`).

**Schema (`InquiryResponse`)**
```jsonc
{
  "id": 12,
  "userId": 5,
  "category": "결제",                   // 계정 / 거래 / 결제 / 배송 / 기타
  "title": "환불 처리가 안 됩니다",
  "content": "어제 17시쯤 결제했는데 ...",
  "email": "user@example.com",          // 답변 받을 이메일 (작성 시점 본인 이메일 스냅샷)
  "status": "PENDING",                  // PENDING / PROCESSING / DONE
  "imageUrls": [],                      // 최대 5장
  "adminReply": null,                   // 답변 전이면 null
  "repliedAt": null,
  "createdAt": "2026-05-03T18:00:00",
  "updatedAt": "2026-05-03T18:00:00"
}
```

**Endpoints (사용자)**
- `POST /api/v1/support/inquiries` — 작성. body: `{category, title, content, email, imageUrls?}` → 201 `data: id`.
- `GET /api/v1/support/inquiries/me?status=&page=&size=` — 본인 목록 (최신순).
- `GET /api/v1/support/inquiries/{id}` — 단건. 본인 것 아니면 `INQUIRY_FORBIDDEN`.
- `DELETE /api/v1/support/inquiries/{id}` — PENDING 상태일 때만. 답변 시작 후엔 `INQUIRY_INVALID_STATE`.

**삭제 정책**
- `PENDING` → 본인 삭제 가능
- `PROCESSING` / `DONE` → 본인 삭제 불가 (감사 추적). 관리자만 hard-delete 가능.

### 10.16 SupportPost (FAQ / QNA — 공개)

관리자가 작성하는 공개 게시글. 비로그인 포함 누구나 GET 조회.

**Schema (`SupportPostResponse`)**
```jsonc
{
  "id": 7,
  "postType": "FAQ",                    // FAQ / QNA
  "category": "결제",
  "question": "결제 후 영수증은 어디서 받을 수 있나요?",
  "answer": "마이페이지 > 결제 내역에서 다운로드 가능합니다.",
  "imageUrls": [],
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Endpoints (공개)**
- `GET /api/v1/support/posts?type=FAQ|QNA&category=&page=&size=` — type 필수 (FAQ 또는 QNA), category 선택. 최신순.
- `GET /api/v1/support/posts/{id}` — 단건.

**카테고리 enum**: `계정 / 거래 / 결제 / 배송 / 기타` (한글 그대로). Inquiry 와 공유.

---

## 11. 관리자 (Admin) 영역

> 모두 `ROLE_ADMIN` 강제. SecurityConfig 의 admin chain 이 다른 도메인과 분리해서 관리.
> 일반 사용자 AT/RT 와 분리된 관리자 AT/RT 사용 — 같은 브라우저에서 두 세션 동시 접속 불가 (쿠키 덮어씀).

### 11.1 관리자 로그인

```
POST /api/v1/auth/admin/login                  → 200 + Set-Cookie: AT, RT (ROLE_ADMIN)
```
Body:
```json
{ "username": "admin", "password": "AdminP@ssw0rd!" }
```
- 실패 시 401 `AUTH_LOGIN_FAILED` (username 존재 여부 노출 X)
- 성공 후 발급되는 AT/RT 는 `ROLE_ADMIN` — `/api/v1/admin/**` 모든 endpoint 호출 가능
- 일반 회원 endpoint (예: `/api/v1/items`) 호출 시 403 (격리)

### 11.2 AdminUser — 회원 관리

```
GET   /api/v1/admin/users?page=&size=          → Page<AdminUserResponse>
GET   /api/v1/admin/users/{id}                 → AdminUserResponse (단건)
PATCH /api/v1/admin/users/{id}/block           → null
  Body: { "blocked": true|false }
  - blocked=true: 로그인 차단 + 토큰 폐기. 즉시 모든 세션 무효화
```

#### AdminUserResponse — 민감 정보 제외
```json
{
  "id": 100,
  "email": "user@example.com",
  "nickname": "쓸랭이",
  "profileImage": "https://...",
  "socialProvider": "LOCAL",
  "blocked": false,
  "deleted": false,
  "trustScore": 4.7,            // null = 리뷰 0건
  "reviewCount": 12,
  "pointBalance": 50000,
  "createdAt": "2026-04-01T12:00:00"
}
```
⚠️ 비밀번호 / social_id / 토큰 등 민감 정보 미노출.

### 11.3 AdminBanner — 메인 배너 CRUD

```
GET    /api/v1/admin/banners?page=&size=       → Page<BannerResponse>  (active=false 포함)
GET    /api/v1/admin/banners/{id}              → BannerResponse
POST   /api/v1/admin/banners                   → 201 + { id }
PATCH  /api/v1/admin/banners/{id}              → null  (전체 수정)
PATCH  /api/v1/admin/banners/{id}/active       → null  (Body: { active: bool })
DELETE /api/v1/admin/banners/{id}              → null  (hard delete)
```

#### BannerUpsertRequest (POST/PATCH 본문)
```json
{
  "title": "여름 세일",                                       // 필수 ≤200
  "imageUrl": "https://cdn.sseulang.test/banners/summer.jpg",  // 필수 ≤500
  "linkUrl": "https://sseulang.test/events/summer",            // optional ≤500
  "sortOrder": 10,                                              // 작을수록 상단
  "startsAt": "2026-05-01T00:00:00",                            // optional, null=즉시
  "endsAt": "2026-05-31T23:59:59"                               // optional, null=무기한
}
```

응답 schema 는 §10.8 BannerResponse 와 동일.

### 11.4 AdminNotice — 공지 CRUD + 토글

```
GET    /api/v1/admin/notices?type=&page=&size= → Page<NoticeResponse>  (미공개 포함)
GET    /api/v1/admin/notices/{id}              → NoticeResponse
POST   /api/v1/admin/notices                   → 201 + { id }
PATCH  /api/v1/admin/notices/{id}              → null  (전체 수정)
PATCH  /api/v1/admin/notices/{id}/pin          → null  (Body: { value: bool })  — 상단 고정 토글
PATCH  /api/v1/admin/notices/{id}/publish      → null  (Body: { value: bool })  — 게시/비게시 토글
DELETE /api/v1/admin/notices/{id}              → null  (hard delete)
```

#### NoticeUpsertRequest (POST/PATCH 본문)
```json
{
  "type": "공지",                                  // 공지 | 이벤트 (한글 enum)
  "title": "5월 거래 수수료 무료 이벤트",           // 필수 ≤200
  "content": "5월 한 달간 모든 거래 수수료가 무료입니다.",  // 필수 (HTML/Markdown 자유)
  "imageUrl": "https://cdn.../notices/202605.jpg", // optional ≤500
  "startsAt": "2026-05-01T00:00:00",                // optional
  "endsAt": "2026-05-31T23:59:59"                   // optional
}
```

응답 schema 는 §10.9 NoticeResponse 와 동일.

### 11.5 AdminReport — 신고 처리

```
GET   /api/v1/admin/reports?status=&page=&size= → Page<AdminReportResponse>
GET   /api/v1/admin/reports/{id}                → AdminReportResponse
PATCH /api/v1/admin/reports/{id}                → null
  Body: { "action": "MARK_IN_PROGRESS"|"COMPLETE"|"REJECT", "memo"?: ≤500 }
  - MARK_IN_PROGRESS: PENDING → IN_PROGRESS (검토 시작)
  - COMPLETE: → COMPLETED (처리 완료)
  - REJECT: → REJECTED (반려)
```

#### AdminReportResponse
```json
{
  "id": 17,
  "reporterId": 100,                  // 신고한 사용자
  "reportedId": 200,                  // 신고당한 사용자
  "itemId": 42,                       // 관련 물품 (없으면 null)
  "reason": "FRAUD",                  // FRAUD / SPAM / HARASSMENT 등
  "detail": "결제 후 물품을 보내지 않습니다",
  "status": "PENDING",                // PENDING / IN_PROGRESS / COMPLETED / REJECTED
  "adminId": null,                    // 처리한 관리자 (PENDING 단계는 null)
  "adminMemo": null,
  "processedAt": null,
  "createdAt": "2026-05-02T10:00:00"
}
```

### 11.6 AdminWithdrawal — 출금 신청 처리

```
GET   /api/v1/admin/withdrawals?status=&page=&size=   → Page<WithdrawalResponse>  (§7 schema 동일)
PATCH /api/v1/admin/withdrawals/{id}                  → null
  Body: { "action": "APPROVE"|"REJECT"|"COMPLETE", "memo"?: ≤500 }
  - APPROVE: 신청 → 승인 (외부 이체 절차 시작)
  - REJECT:  신청 → 거부 (잔액 자동 환불)
  - COMPLETE: 승인 → 완료 (외부 이체 완료 표시)
```

⚠️ 잔액 차감은 사용자 출금 신청 시점 (`POST /withdrawals`) 에 이미 발생. REJECT 시 자동 환불은 백엔드가 atomic 처리.

### 11.7 AdminStats — Dashboard

```
GET /api/v1/admin/stats/dashboard               → AdminDashboardResponse
```
5개 도메인 (User / Transaction / Payment / Withdrawal / Delivery) 통계 묶음 — 단일 호출 11 SQL.

#### AdminDashboardResponse
```json
{
  "users": {
    "total": 1234,           // 탈퇴/차단 포함
    "blocked": 12,
    "deleted": 8,
    "active": 1214
  },
  "transactions": {
    "total": 568,
    "byStatus": {            // 모든 status 포함 (0건도 0L)
      "채팅중": 120,
      "예약": 35,
      "거래완료": 380,
      "취소": 33
    }
  },
  "payments": {
    "paidCount": 342,
    "totalPaidAmount": 12500000
  },
  "withdrawals": {
    "total": 87,
    "byStatus": { "신청": 3, "승인": 1, "완료": 80, "거부": 3 },
    "completedAmount": 8000000
  },
  "deliveries": {
    "total": 42,
    "byStatus": { "모집중": 3, "수락": 2, "배송중": 1, "배송완료": 1, "정산완료": 33, "취소": 2 },
    "settledFeeTotal": 165000
  }
}
```

### 11.8 AdminInquiry — 1:1 문의 답변/관리

본 도메인은 `Inquiry` (사용자용 §10.15) 와 동일 데이터. 응답 schema 동일.

- `GET /api/v1/admin/inquiries?status=&page=&size=` — 전체 목록 (status 필터 선택, 최신순)
- `GET /api/v1/admin/inquiries/{id}` — 단건
- `PATCH /api/v1/admin/inquiries/{id}/reply` — 답변 작성. body: `{adminReply, status?}`. status 미지정 시 자동 `DONE`.
- `PATCH /api/v1/admin/inquiries/{id}/status` — body: `{status}`. 답변 없이 `DONE` 시도 시 400.
- `DELETE /api/v1/admin/inquiries/{id}` — hard delete. 사용자 본인 삭제와 달리 상태 제약 없음.

### 11.9 AdminSupportPost — FAQ/QNA CRUD

- `POST /api/v1/admin/support/posts` — body: `{postType, category, question, answer, imageUrls?}` → 201 `data: id`
- `PUT /api/v1/admin/support/posts/{id}` — 전체 수정 (body 동일)
- `DELETE /api/v1/admin/support/posts/{id}` — hard delete

조회는 사용자 endpoint (`GET /api/v1/support/posts`, §10.16) 를 그대로 사용.

### 11.10 admin 호출 시 흔한 함정

| 증상 | 원인 |
|---|---|
| `/api/v1/admin/**` 401 | 일반 사용자 AT 로 호출 — admin 로그인 필요 |
| `/api/v1/items` 등 일반 endpoint 가 admin 토큰으로 403 | ROLE_ADMIN 은 일반 영역 차단 (격리). 사용자 화면 보려면 별도 일반 계정 |
| 동일 브라우저에서 admin/일반 동시 사용 불가 | 쿠키 덮어쓰기 — admin 패널은 별도 도메인/브라우저 권장 |

---

## 12. 페이징 표준

```http
GET /api/v1/items?page=0&size=20&...
```
- `page`: 0-based. 음수는 0으로 보정.
- `size`: 1~100. 범위 밖은 클램프.

### 채팅 메시지 (커서)
```http
GET /api/v1/chat-rooms/{id}/messages?before={messageId}&size=30
```
- `before` 생략하면 최신부터 size 만큼.
- `before` 는 MongoDB ObjectId hex string (Long 아님).

---

## 13. 환경별 차이 요약

| 항목 | local | prod |
|---|---|---|
| Cookie domain | `localhost` | `${COOKIE_DOMAIN}` (예: `.sseulang.com`) |
| Cookie secure | `false` | `true` (HTTPS 필수) |
| Cookie SameSite | `Lax` | `Strict` |
| CORS origins | `localhost:3000`, `localhost:5173` | `${CORS_ALLOWED_ORIGINS}` |
| Toss 키 | 테스트 키 | 운영 키 |
| dev-auth bypass | `enabled: true` (`/dev/auth/login`) | **반드시 false** |
| Email sender | 로그만 (`LogEmailSender`) | SMTP (`SmtpEmailSender`) |
| JVM TZ | KST | KST 강제 (`-Duser.timezone=Asia/Seoul`) |

### dev-auth bypass (로컬 전용)
```http
POST /dev/auth/login
{ "userId": 1 }
```
→ 즉시 AT/RT 쿠키 발급 (회원가입/이메일 인증 우회). prod 에서는 비활성.

---

## 14. 흔한 통합 이슈 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 모든 요청 401 | 쿠키 미동봉 — `withCredentials: true` 누락 |
| POST 만 403 / `INVALID_CSRF_TOKEN` | `X-XSRF-TOKEN` 헤더 누락 또는 쿠키와 불일치 |
| `AUTH_EMAIL_NOT_VERIFIED` 자주 발생 | 신규 가입 사용자가 인증 안 함 — `/api/v1/auth/verify-email` 안내 |
| WebSocket CONNECT 즉시 끊김 | Origin 화이트리스트 누락 또는 인증 누락 |
| Item 등록 후 이미지 안 보임 | imageUrl 의 `items/{userId}/` 가 `items/{itemId}/` 로 promote 됐는지 확인 (응답 imageUrls 사용) |
| 결제 후 잔액 미반영 | webhook 도착 전 — `GET /api/v1/payments/{id}` 폴링 또는 reconciliation scheduler (5분) 대기 |
| 채팅 unread 카운터 0 으로 안 됨 | `PATCH /chat-rooms/{id}/read` 안 부른 것 — 채팅방 진입 시 호출 |
| 해외 출장자 시간 1시간 어긋남 | LocalDateTime 은 KST naive — `parseKst()` 헬퍼로 강제 해석 |
| Message.createdAt 만 형식 다름 | Instant 타입 (UTC offset). 다른 도메인 LocalDateTime 과 혼동 주의 |

---

## 15. 참조 문서

- Swagger UI: 모든 endpoint 명세 + Request/Response 예시.
- `docs/AUTH_SECURITY.md` — 인증/보안 결정 사항.
- `docs/SSEULANG_BACKEND_GUIDE.md` — 도메인 결정 사항.
- `docs/migration/V4_DEPLOYMENT.md` — DB 마이그 절차.
- `CLAUDE.md` — 백엔드 코딩 컨벤션.

---

## 16. 합의 필요 항목 (PM/프론트와 합의 후 확정)

- [ ] prod 도메인 + CORS origins 확정
- [ ] 쿠키 도메인 (`.sseulang.com` vs subdomain)
- [ ] WebSocket prod 엔드포인트 (`wss://` 인증서)
- [ ] Swagger prod 노출 정책 (인증 필요 / IP 제한 / 비활성)

PM·프론트 합의 후 본 문서 갱신 + `application-prod.yml` 환경변수 채움.

---

## 17. 변경 이력

- **2026-05-14 (라운드 14)** — 거래대행 대여 lifecycle 추가: `사용중/반납중`, `request-return/confirm-return`, `rentalEndAt` 자동 반납, 보증금 hold/refund, seller/itemPrice 정산, return fee 차감. Toss webhook signature/timestamp 헤더와 processed_at 보류 정책 반영.
- **2026-05-03 (라운드 7)** — 고객지원 도메인 추가: §10.15 Inquiry / §10.16 SupportPost (FAQ·QNA), §11.8 AdminInquiry / §11.9 AdminSupportPost. presigned `purpose=SUPPORT` 추가, ErrorCode `INQUIRY_NOT_FOUND/FORBIDDEN/INVALID_STATE` + `SUPPORT_POST_NOT_FOUND` 추가. §11.8 흔한 함정 → §11.10.
- **2026-05-03 (라운드 6)** — §11 관리자(Admin) 영역 신설 — AdminAuth/User/Banner/Notice/Report/Withdrawal/Stats 7개 endpoint group + Dashboard schema. 페이징/환경/트러블슈팅 §12~17 재번호.
- **2026-05-03 (라운드 5 보강)** — §10 도메인 schema 누락분 추가 (Banner/Notice/Notification/UserBlock/Review/Delivery 6개).
- **2026-05-03 (라운드 5)** — 채팅 도메인 schema 정확성 보강, STOMP destination 잘못된 매핑 수정, Item/ChatRoom 부분 편집 endpoint 추가, 도메인 schema 통합 §10 신설.
- 2026-05-02 (라운드 4) — `GET /users/me/transactions` 추가, 거래 도메인 명세 정리.
- 2026-05-02 (라운드 3) — 판매자 프로필 + 포인트 히스토리 + Item 이미지 부분편집 endpoint 추가.
- 2026-05-02 (라운드 2) — ItemSummary 카드 필드 (thumbnail/wishlistCount/isWishlisted), 마이페이지 본인 물품, 찜 토글 응답 변경.
- 2026-05-02 (라운드 1 hotfix) — CORS preflight, MeResponse, OAuth code grant, PATCH /me, sort, RentalUnit 정정.
- 2026-04-28 (초안) — 인증/응답/CORS/WebSocket/S3 기본.
