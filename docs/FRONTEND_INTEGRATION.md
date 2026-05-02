# 프론트엔드 연동 가이드

> 쓸랭 백엔드 + 프론트엔드 연동을 위한 종합 가이드. 최신 갱신: 2026-05-03 (라운드 5 — 채팅 schema/STOMP 정확성 보강).

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
| `ITEM_IMAGE_LIMIT_EXCEEDED` | 400 | 이미지 5장 초과 |
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

POST   /api/v1/items/{id}/images               — 이미지 부분 추가 (5장 한도)
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
  "imageUrls": ["https://..."],           // optional, 최대 5장
  "hashtags": ["아이폰"]                  // optional
}
```
- **PATCH 의 imageUrls non-null = 전체 교체** (4장 유지하려면 4장 다시 보내야 함). 부분 편집은 분리 endpoint 사용.

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
채팅중 → 예약 → 거래완료
   └─→ 취소 (채팅중/예약 양쪽에서 가능)
```
- 상태값은 모두 **한글**: `채팅중 | 예약 | 거래완료 | 취소`. "진행중" 없음.

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
  Body: { action: "예약"|"거래완료"|"취소", cancelReason?: string }
  - 예약: seller, 채팅중→예약, Item 자동 → 예약
  - 거래완료: seller, 예약→거래완료, Item 자동 → 거래완료, 포인트 정산
  - 취소: 양쪽 참여자, Item 예약이었으면 → 판매중 복원

GET   /api/v1/users/me/transactions?role=buyer|seller&status=&page=&size=
  - role 미지정 = 양쪽. status 미지정 = 전체
```

⚠️ **거래 정산은 잔액 이동만** (PG 안 거침). PaymentResponse.transactionId 는 항상 null. 거래 추적은 PointHistory.

⚠️ **대여 반납 별도 endpoint 없음** — 일반 거래완료와 동일하게 PATCH action=거래완료. 보증금 환불 흐름 미지원 (관리자 수동, Day 9+).

### 10.4 ChatRoom

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

### 10.5 Message

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

### 10.6 Report (신고)

```
POST /api/v1/items/{itemId}/report             → 201 + { id } (이메일 인증)
POST /api/v1/users/{userId}/report             → 201 + { id } (이메일 인증)

Body:
{
  "reason": "사기 의심",                      // 필수 ≤50
  "detail": "결제 후 연락 끊김..."             // optional ≤5000
}
```

### 10.7 Category

```
GET /api/v1/categories                         → 활성 카테고리 트리 (root → children 재귀)
GET /api/v1/categories/{id}                    → 단건
```
- DB 시드된 트리 (2단 깊이). enum 고정 X.

### 10.8 Delivery (배달대행)

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

#### WebSocket / 실시간 위치 — **❌ 현재 미지원**
- `/topic/delivery/{id}/location` 같은 destination 미구현
- DB 에 좌표 컬럼 없음 (`Item.lat/lng`, `DeliveryRequest.lat/lng` 모두 X)
- GitHub issue #51 로 follow-up 등록 — **5/6 이후** (라이더 모바일 클라이언트 합류 후)
- 그때까지 진행 상황은 폴링 (`GET /deliveries/{id}` refreshInterval 5초) 또는 status 변경 알림으로 대응

#### 활용 예시
```jsx
// 라이더
await api.patch(`/deliveries/${id}/accept`);   // → 수락
await api.patch(`/deliveries/${id}/pickup`);   // → 배송중
await api.patch(`/deliveries/${id}/deliver`);  // → 배송완료

// 요청자
await api.post('/deliveries', { pickupAddress, dropoffAddress, itemDescription, fee });
await api.patch(`/deliveries/${id}/complete`); // → 정산완료, 잔액 이동
```

---

## 11. 페이징 표준

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

## 12. 환경별 차이 요약

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

## 13. 흔한 통합 이슈 트러블슈팅

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

## 14. 참조 문서

- Swagger UI: 모든 endpoint 명세 + Request/Response 예시.
- `docs/AUTH_SECURITY.md` — 인증/보안 결정 사항.
- `docs/SSEULANG_BACKEND_GUIDE.md` — 도메인 결정 사항.
- `docs/migration/V4_DEPLOYMENT.md` — DB 마이그 절차.
- `CLAUDE.md` — 백엔드 코딩 컨벤션.

---

## 15. 합의 필요 항목 (PM/프론트와 합의 후 확정)

- [ ] prod 도메인 + CORS origins 확정
- [ ] 쿠키 도메인 (`.sseulang.com` vs subdomain)
- [ ] WebSocket prod 엔드포인트 (`wss://` 인증서)
- [ ] Swagger prod 노출 정책 (인증 필요 / IP 제한 / 비활성)

PM·프론트 합의 후 본 문서 갱신 + `application-prod.yml` 환경변수 채움.

---

## 16. 변경 이력

- **2026-05-03 (라운드 5)** — 채팅 도메인 schema 정확성 보강, STOMP destination 잘못된 매핑 수정, Item/ChatRoom 부분 편집 endpoint 추가, 도메인 schema 통합 §10 신설.
- 2026-05-02 (라운드 4) — `GET /users/me/transactions` 추가, 거래 도메인 명세 정리.
- 2026-05-02 (라운드 3) — 판매자 프로필 + 포인트 히스토리 + Item 이미지 부분편집 endpoint 추가.
- 2026-05-02 (라운드 2) — ItemSummary 카드 필드 (thumbnail/wishlistCount/isWishlisted), 마이페이지 본인 물품, 찜 토글 응답 변경.
- 2026-05-02 (라운드 1 hotfix) — CORS preflight, MeResponse, OAuth code grant, PATCH /me, sort, RentalUnit 정정.
- 2026-04-28 (초안) — 인증/응답/CORS/WebSocket/S3 기본.
