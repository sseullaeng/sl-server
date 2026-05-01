# 프론트엔드 연동 가이드

> 쓸랭 백엔드 + 프론트엔드 연동을 위한 종합 가이드. 5/6 마감 → 5/11 연동·배포 페이즈에 사용.

## 0. 빠른 시작

| 항목 | 로컬 | 프로덕션 |
|---|---|---|
| Base URL | `http://localhost:8080` | (배포 후 결정) |
| Swagger | `http://localhost:8080/swagger-ui.html` | (정책 결정 중 — `/swagger-ui.html`) |
| WebSocket | `http://localhost:8080/ws-stomp` (SockJS) / `/ws-stomp-native` (native) | 동일 |
| 응답 포맷 | `{success, data, error}` | 동일 |

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
POST /api/v1/auth/login        (이메일/비밀번호) → 200 + Set-Cookie: AT, RT
POST /api/v1/auth/oauth2/{provider}  (KAKAO|GOOGLE, accessToken) → 200 + Set-Cookie: AT, RT
```

응답 쿠키 (서버가 자동 설정):
- `access_token` — HttpOnly, Secure(prod), SameSite=Strict(prod)/Lax(local), Max-Age=1800
- `refresh_token` — HttpOnly, Secure(prod), SameSite=Strict(prod)/Lax(local), Max-Age=604800
- `XSRF-TOKEN` — HttpOnly **X** (JS에서 읽어 CSRF 헤더로 다시 보내야 함)

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
응답: 새 AT + RT 발급 (RT rotation). 실패 시 `401 AUTH_REFRESH_TOKEN_INVALID` → 로그인 화면.

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

### 2.5 이메일 인증 가드

`signup` 직후 사용자는 `email_verified=false`. 다음 API 는 `403 AUTH_EMAIL_NOT_VERIFIED` 떨굼:
- 거래/결제/포인트 출금/배달대행 등 자금 영향
- 채팅방 개설 / 메시지 전송
- 신고
- 파일 presigned URL 발급

→ 인증 메일 링크 클릭 → `POST /api/v1/auth/verify-email?token=...` 호출하면 `email_verified=true`.

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
| `AUTH_OAUTH_FAILED` | 401 | OAuth provider 검증 실패 | 토큰 재발급 후 재시도 |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | 이메일 인증 필요 | 인증 메일 안내 모달 |
| `AUTH_VERIFICATION_TOKEN_INVALID` | 400 | 잘못된 인증 토큰 | "유효하지 않은 링크" |
| `AUTH_VERIFICATION_TOKEN_EXPIRED` | 400 | 만료된 인증 토큰 | resend 버튼 |
| `AUTH_VERIFICATION_RESEND_TOO_SOON` | 429 | rate limit | "잠시 후 다시 시도" |
| `AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER` | 409 | 같은 이메일 다른 SNS | "이미 X로 가입된 이메일" |
| `USER_BLOCKED` | 403 | 차단 계정 | 로그인 차단 화면 |

### 거래/결제/포인트
| code | HTTP | 의미 |
|---|---|---|
| `ITEM_NOT_FOUND` | 404 | 물품 없음 또는 삭제됨 |
| `ITEM_INVALID_STATE` | 400 | 비공개/예약 등 현재 상태 불가 |
| `TRANSACTION_RESERVED_BY_OTHER` | 409 | 다른 사용자가 먼저 예약 |
| `TRANSACTION_INVALID_STATE` | 400 | 거래 상태 머신 위반 |
| `TRANSACTION_SELF_NOT_ALLOWED` | 400 | 본인 물품에 거래 시도 |
| `INSUFFICIENT_POINT` | 400 | 포인트 부족 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 토스 결제 금액 위변조 |
| `PAYMENT_DUPLICATED` | 409 | 같은 merchant_uid 재결제 |
| `WITHDRAWAL_IDEMPOTENCY_MISMATCH` | 409 | 같은 idempotencyKey 다른 내용 |
| `REVIEW_DUPLICATED` | 409 | 같은 거래 본인 리뷰 중복 |
| `REVIEW_PERIOD_EXPIRED` | 400 | 7일 지남 |

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

## 5. OAuth 흐름 (카카오/구글)

```
[Frontend]                     [SDK]                    [Backend]
   │                              │                         │
   │── 로그인 버튼 클릭 ──────────►│                         │
   │                              │── 사용자 동의 화면 ────►│
   │                              │◄── access_token ────────│
   │◄── access_token ─────────────│                         │
   │                                                         │
   │── POST /api/v1/auth/oauth2/{provider} ──────────────►   │
   │   { accessToken: "..." }                                │
   │                                                         │
   │◄── 200 + Set-Cookie: AT, RT ──────────────────────────  │
   │   { success: true, data: { userId, email, ... } }       │
```

### Provider 별 SDK 가이드
- **KAKAO**: `Kakao.Auth.login()` → `getAccessToken()` → 백엔드 호출
- **GOOGLE**: `tokenClient = google.accounts.oauth2.initTokenClient({...})` → `requestAccessToken()` → `access_token` → 백엔드 호출

### 첫 가입 vs 재로그인
백엔드가 자동 분기 — SocialId 매핑 있으면 로그인, 없으면 신규 가입 + email_verified=true (provider 가 검증한 이메일).

### LOCAL 가입 사용자가 OAuth 호출 시
같은 이메일이면 takeover (provider 마이그레이션) — 사용자 동의 절차 후 진행. 자세히는 백엔드와 별도 합의.

---

## 6. 결제 흐름 (토스페이먼츠)

```
[Frontend]                      [Backend]                  [Toss]
   │                               │                         │
   │── POST /api/v1/payments/start ►                         │
   │   { amount }                  │                         │
   │◄── { merchantUid } ───────────│                         │
   │                                                         │
   │── Toss SDK 결제창 호출 ──────────────────────────────►   │
   │   { merchantUid, amount, ... }                          │
   │                                                         │
   │◄── 결제 성공 콜백 ──────────────────────────────────────│
   │   { paymentKey, orderId, amount }                       │
   │                                                         │
   │── POST /api/v1/payments/confirm ►                       │
   │   { paymentKey, orderId, amount }                       │
   │                                  ── 토스 confirm API ─►│
   │                                  ◄── 검증 OK ──────────│
   │◄── 200 + 잔액 충전 완료 ──────│                         │
```

### 핵심 룰
- `merchantUid` 는 백엔드가 발급 (UNIQUE) — 프론트 임의 생성 금지.
- confirm 호출 시 amount 가 토스 응답과 mismatch → `PAYMENT_AMOUNT_MISMATCH` (위변조 차단).
- 같은 `merchantUid` 재호출 → `PAYMENT_DUPLICATED` (멱등성).
- 결제 페이지 자체는 토스 SDK가 렌더 — 별도 백엔드 페이지 X.

---

## 7. S3 파일 업로드 (presigned URL)

```
[Frontend]                       [Backend]                       [S3]
   │                                │                              │
   │── POST /api/v1/files/presigned-url ─►                         │
   │   { purpose: "ITEM",           │                              │
   │     files: [{contentType, contentLength}] }                   │
   │◄── { uploads: [{presignedUrl, key}] } ────│                   │
   │                                                                │
   │── PUT {presignedUrl} ──────────────────────────────────────►  │
   │   body: <binary>                                               │
   │   Content-Type: image/jpeg                                     │
   │◄── 200 ──────────────────────────────────────────────────────│
   │                                                                │
   │── POST /api/v1/items ─────────►                                │
   │   { ..., imageUrls: [<key 또는 GET URL>] }                     │
   │   백엔드가 items/{userId}/* → items/{itemId}/* S3 copy        │
   │◄── 201 + itemId ──────────────│                              │
```

### 룰
- `purpose`: `PROFILE` 또는 `ITEM` (일반 사용자). 그 외는 도메인 전용 endpoint.
- Content-Type: `image/jpeg|jpg|png|webp|gif` 만 허용.
- Content-Length: ≤ 5MB.
- 파일 한 번에 ≤ 10건.
- presigned URL 만료: 5분.
- 응답의 `key` 필드를 그대로 Item 등록 요청의 `imageUrls` 에 포함시키면 백엔드가 정식 폴더로 promote.

---

## 8. WebSocket / STOMP (실시간 채팅·알림)

### 8.1 SockJS 클라이언트 (브라우저, 쿠키 기반)

```js
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws-stomp'),
  // 쿠키 자동 동봉 — handshake 단계에서 SecurityContext 주입
  onConnect: () => {
    // 채팅방 토픽 구독
    client.subscribe('/topic/chat-room/123', msg => { ... });
    // 본인 알림 큐 구독 (Spring 자동 라우팅)
    client.subscribe('/user/queue/notifications', msg => { ... });
  },
});
client.activate();
```

### 8.2 Native WebSocket 클라이언트 (모바일, 쿠키 X)

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

### 8.3 메시지 전송
```js
client.publish({
  destination: '/app/chat/send',
  body: JSON.stringify({ chatRoomId: 123, content: '안녕' }),
  headers: { 'X-XSRF-TOKEN': xsrf },  // SockJS 면 필요
});
```
또는 REST `POST /api/v1/chat-rooms/{id}/messages` — 어느 쪽이든 결과는 동일하게 broadcast.

### 8.4 destination 화이트리스트
| destination | 의미 | 권한 |
|---|---|---|
| `/topic/chat-room/{roomId}` | 채팅방 메시지 broadcast | 참여자만 |
| `/user/queue/messages` | 본인 메시지 큐 (Spring 자동) | 본인 |
| `/user/queue/notifications` | 본인 알림 큐 | 본인 |

그 외 destination subscribe → `FORBIDDEN`.

---

## 9. 페이징 표준

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

---

## 10. 환경별 차이 요약

| 항목 | local | prod |
|---|---|---|
| Cookie domain | `localhost` | `${COOKIE_DOMAIN}` (예: `.sseulang.com`) |
| Cookie secure | `false` | `true` (HTTPS 필수) |
| Cookie SameSite | `Lax` | `Strict` |
| CORS origins | `localhost:3000`, `localhost:5173` | `${CORS_ALLOWED_ORIGINS}` |
| Toss 키 | 테스트 키 | 운영 키 |
| dev-auth bypass | `enabled: true` (`/dev/auth/login`) | **반드시 false** |
| Email sender | 로그만 (`LogEmailSender`) | SMTP (`SmtpEmailSender`) |

### dev-auth bypass (로컬 전용)
```http
POST /dev/auth/login
{ "userId": 1 }
```
→ 즉시 AT/RT 쿠키 발급 (회원가입/이메일 인증 우회). prod 에서는 비활성.

---

## 11. 흔한 통합 이슈 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 모든 요청 401 | 쿠키 미동봉 — `withCredentials: true` 누락 |
| POST 만 403 / `INVALID_CSRF_TOKEN` | `X-XSRF-TOKEN` 헤더 누락 또는 쿠키와 불일치 |
| `AUTH_EMAIL_NOT_VERIFIED` 자주 발생 | 신규 가입 사용자가 인증 안 함 — `/api/v1/auth/verify-email` 안내 |
| WebSocket CONNECT 즉시 끊김 | Origin 화이트리스트 누락 또는 인증 누락 |
| Item 등록 후 이미지 안 보임 | imageUrl 의 `items/{userId}/` 가 `items/{itemId}/` 로 promote 됐는지 확인 (응답 imageUrls 사용) |
| 결제 후 잔액 미반영 | webhook 도착 전 — `GET /api/v1/payments/{paymentKey}` 폴링 또는 reconciliation scheduler (5분) 대기 |

---

## 12. 참조 문서

- Swagger UI: 모든 endpoint 명세 + Request/Response 예시 (PR #48 에서 보강됨).
- `docs/AUTH_SECURITY.md` — 인증/보안 결정 사항.
- `docs/SSEULANG_BACKEND_GUIDE.md` — 도메인 결정 사항.
- `docs/migration/V4_DEPLOYMENT.md` — DB 마이그 절차.
- `CLAUDE.md` — 백엔드 코딩 컨벤션.

---

## 13. 합의 필요 항목 (PM/프론트와 합의 후 확정)

- [ ] prod 도메인 + CORS origins 확정
- [ ] 쿠키 도메인 (`.sseulang.com` vs subdomain)
- [ ] OAuth redirect URI (현재 백엔드는 access_token POST 받는 구조 — frontend 가 SDK 직접 호출)
- [ ] WebSocket prod 엔드포인트 (`wss://` 인증서)
- [ ] Swagger prod 노출 정책 (인증 필요 / IP 제한 / 비활성)

PM·프론트 합의 후 본 문서 갱신 + `application-prod.yml` 환경변수 채움.
