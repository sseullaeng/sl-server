# 프론트 페이지 구성 및 구성요소 전달 문서

> 백엔드 연동 확인용 화면 구조 정리
> 기준일: 2026-05-14
> 기준 코드: `src/app/router.tsx`, `src/pages/**`, `src/features/**/api.ts`

## 1. 전체 구조

프론트는 Vite + React + TypeScript 기반 SPA입니다. 라우팅은 React Router의 `createBrowserRouter`를 사용하고, 페이지 컴포넌트는 전부 lazy import로 분리되어 있습니다.

```text
src/
├── app/                 # App, router, provider, STOMP provider
├── pages/               # 라우트 단위 화면
├── features/            # 도메인별 api, hook, type, 도메인 컴포넌트
├── shared/
│   ├── api/             # axios, upload helper
│   ├── lib/             # date, stomp, kakao map, util
│   ├── store/           # drawer/notice 등 공통 UI 상태
│   ├── types/           # 공통 응답/유저 타입
│   └── ui/              # 공통 UI 컴포넌트
└── styles/              # 전역 Tailwind CSS
```

주요 원칙은 `pages -> features -> shared` 방향입니다. 화면은 `pages`에서 조립하고, 서버 통신은 `features/*/api.ts` 또는 공통 `shared/api`를 통해 처리합니다.

## 2. 공통 레이아웃 및 전역 구성요소

| 구성요소 | 파일 | 역할 | 백엔드 영향 |
|---|---|---|---|
| `RootLayout` | `src/shared/ui/RootLayout.tsx` | Header, main content, Footer, SideDrawer를 감싸는 공통 레이아웃 | 로그인 상태, 알림/채팅 데이터가 전역 UI에 노출됨 |
| `Header` | `src/shared/ui/Header.tsx` | 상단 네비게이션, 인증 상태별 메뉴, 모바일 메뉴 | `GET /api/v1/users/me`, 알림 unread count |
| `SideDrawer` | `src/shared/ui/SideDrawer.tsx` | 우측 드로어. 채팅방 목록/대화/알림 패널 포함 | 채팅, 거래 생성/상태 변경, 알림 API와 STOMP 필요 |
| `Footer` | `src/shared/ui/Footer.tsx` | 하단 안내 영역 | 없음 |
| `ProtectedRoute` | `src/shared/ui/ProtectedRoute.tsx` | 로그인 사용자 전용 라우트 | 인증 실패 시 401/refresh 정책 필요 |
| `AdminRoute` | `src/shared/ui/AdminRoute.tsx` | 관리자 전용 라우트. `/api/v1/admin/me` 기준 판단 | 일반 유저 세션과 관리자 세션 분리 필요 |
| `PublicOnlyRoute` | `src/shared/ui/PublicOnlyRoute.tsx` | 로그인 사용자의 로그인/회원가입 접근 방지 | 없음 |
| `Button`, `Input`, `Card`, `Modal` | `src/shared/ui/*` | 공통 UI primitive | 없음 |
| `ReportModal` | `src/shared/ui/ReportModal.tsx` | 물품/사용자 신고 공통 모달 | 신고 사유, 상세 내용 저장 API 필요 |
| `UserProfileFloat` | `src/shared/ui/UserProfileFloat.tsx` | 판매자/사용자 프로필 플로팅 패널 | 공개 프로필, 받은 리뷰, 판매 물품 조회 |
| `KakaoMap`, `KakaoAddressSearch` | `src/shared/ui/*` | 지도 표시, 주소/좌표 검색 | 주소 문자열과 좌표 필드 저장 필요 |
| `NotificationDropdown` | `src/features/notification/components/NotificationDropdown.tsx` | 헤더 알림 드롭다운 | 알림 linkType/linkId 라우팅 규격 필요 |

## 3. 공통 API 규칙

| 항목 | 프론트 기대사항 |
|---|---|
| Base URL | `VITE_API_BASE`, 미설정 시 상대 경로 |
| 인증 | HttpOnly 쿠키 기반, `withCredentials: true` |
| CSRF | `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 전송 |
| 응답 래핑 | `{ success: true, data }` 또는 `{ success: false, error }` |
| 응답 처리 | axios interceptor가 성공 응답의 `data`만 unwrap |
| 토큰 갱신 | `AUTH_TOKEN_EXPIRED` 발생 시 `POST /api/v1/auth/refresh` 후 원 요청 1회 재시도 |
| 세션 만료 | refresh 실패, `AUTH_REFRESH_TOKEN_INVALID`, `USER_BLOCKED` 시 로그아웃 이벤트 |
| 페이징 | `PageResponse<T> = { content, page, size, totalElements, totalPages, hasNext, hasPrevious }` |
| 이미지 업로드 | presigned URL 발급 -> S3 PUT -> 도메인 API에 key 또는 URL 전달 |
| STOMP | `VITE_WS_URL` 또는 기본 `http://localhost:8080/ws-stomp`, SockJS 사용 |

## 4. 라우트 접근 구분

| 구분 | 라우트 |
|---|---|
| 비로그인 전용 | `/login`, `/signup` |
| 공개 접근 | `/`, `/categories/:id`, `/items`, `/items/:id`, `/notices`, `/notices/:id`, `/support`, `/terms`, 일부 거래대행 링크 라우트 |
| 로그인 필수 | 물품 등록/수정, 포인트, 배송, 마이페이지, 알림, 리뷰, 내 거래대행 목록 |
| 관리자 | `/admin/login`, `/admin/**` |
| 404 | `*` |

주의: 일부 관리자 작성 화면(`/notices/write`, `/support/posts/new`)과 일부 거래대행 진행 화면(`/escrow/internal/new`, `/escrow/:id/buyer-info`, `/escrow/:id/pay`)은 라우터상 공개 레이아웃 아래에 있으나, 실제 데이터 변경은 백엔드 권한 검증이 반드시 필요합니다. 프론트 라우트 가드만 신뢰하면 안 됩니다.

## 5. 화면별 구성 및 필요 API

### 5.1 인증

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/login` | `LoginPage` | 이메일 로그인, 카카오/구글 로그인 진입 | `POST /api/v1/auth/login`, `POST /api/v1/auth/oauth2/{provider}` |
| `/signup` | `SignupPage` | 이메일, 닉네임, 비밀번호 회원가입 폼 | `POST /api/v1/auth/signup` |
| `/auth/:provider/callback` | `SocialCallbackPage` | OAuth redirect code 처리 | `POST /api/v1/auth/oauth2/{provider}` |
| `/auth/verify-email` | `VerifyEmailPage` | 이메일 인증 토큰 처리, 재발송 | `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/resend-verification` |
| 전역 | `auth/store`, `ProtectedRoute` | 로그인 상태 저장, 새로고침 시 사용자 복구 | `GET /api/v1/users/me`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |

### 5.2 홈, 카테고리, 물품

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/` | `HomePage` | 메인 배너, HOT/대여/판매 섹션, 물품 카드 | `GET /api/v1/banners`, `GET /api/v1/items` |
| `/categories/:id` | `CategoryPage` | 카테고리별 물품 목록 | `GET /api/v1/categories`, `GET /api/v1/categories/{id}`, `GET /api/v1/items?categoryId=` |
| `/items` | `ItemListPage` | 검색, 카테고리, 거래유형, 가격, 태그, 정렬 필터 | `GET /api/v1/items`, `GET /api/v1/categories`, `GET /api/v1/categories/search` |
| `/items/:id` | `ItemDetailPage` | 이미지, 판매자 프로필, 찜, 신고, 채팅 시작, 대여 기간 선택 | `GET /api/v1/items/{id}`, `GET /api/v1/items/{id}/rental-blocks`, `POST /api/v1/chat-rooms`, `POST/DELETE /api/v1/items/{id}/wishlist`, `POST /api/v1/items/{id}/report`, `POST /api/v1/items/{id}/rental-request` |
| `/items/new` | `ItemCreatePage` | 물품 등록 폼, 카테고리 선택, 주소 선택, 이미지 업로드 | `POST /api/v1/files/presigned-url`, `POST /api/v1/items`, `GET /api/v1/categories` |
| `/items/:id/edit` | `ItemEditPage` | 물품 수정, 이미지 추가/삭제/정렬 | `GET /api/v1/items/{id}`, `PATCH /api/v1/items/{id}`, `POST/DELETE/PATCH /api/v1/items/{id}/images*`, `POST /api/v1/files/presigned-url` |
| `/mypage/items` | `MyItemsPage` | 내 물품, 내 거래 목록 일부 표시 | `GET /api/v1/users/me/items`, `GET /api/v1/users/me/transactions` |
| `/mypage/wishes` | `WishListPage` | 찜 목록, 찜 해제 | `GET /api/v1/users/me/wishlist`, `DELETE /api/v1/items/{id}/wishlist` |

물품 목록의 백엔드 query param은 현재 `q`, `categoryId`, `tradeType`, `minPrice`, `maxPrice`, `tag`, `sort`, `sellerId`, `page`, `size` 사용을 전제로 합니다.

### 5.3 채팅 및 거래

| 라우트/위치 | 페이지/컴포넌트 | 주요 구성요소/기능 | 필요 API/STOMP |
|---|---|---|---|
| 전역 드로어 | `SideDrawer` | 채팅방 목록, 메시지 목록, 메시지 전송, 읽음 처리, 방 나가기 | `GET /api/v1/chat-rooms`, `GET /api/v1/chat-rooms/{id}`, `GET /api/v1/chat-rooms/{id}/messages`, `POST /api/v1/chat-rooms/{id}/messages`, `PATCH /api/v1/chat-rooms/{id}/read`, `PATCH /api/v1/chat-rooms/{id}/leave` |
| 물품 상세 | `ItemDetailPage` | 채팅방 생성 | `POST /api/v1/chat-rooms` |
| 채팅 내부 | `SideDrawer` | 거래 생성, 예약/인계확인/인수확인/완료/반납요청/회신확인/취소 상태 전이 | `POST /api/v1/transactions`, `PATCH /api/v1/transactions/{id}` |
| `/trades/:tradeId` | `TradeDetailPage` | 거래 상세, 상태별 액션, 리뷰 작성 이동 | `GET /api/v1/transactions/{id}`, `PATCH /api/v1/transactions/{id}` |
| `/transactions/:id` | `TransactionPage` | 현재 기본 레이아웃만 있음 | 구현 예정 |
| STOMP | `shared/lib/stomp.ts` | 실시간 메시지/알림 수신 | `/user/queue/messages`, `/user/queue/notifications` 등 서버 topic 규격 필요 |

참고: 메시지 전송은 현재 REST 전송을 사용하고, 백엔드가 저장 후 STOMP broadcast하는 흐름입니다.

직접거래는 플랫폼 포인트 정산이 없습니다. 일반 판매/나눔은 `채팅중 -> 예약 -> 인계완료 -> 거래완료`, 직접 대여는 `채팅중 -> 예약 -> 인계완료 -> 반납요청 -> 거래완료` 흐름을 사용합니다. 거래대행 대여는 아래 Escrow 전용 API의 `request-return`/`confirm-return` 흐름을 사용하므로 직접거래 PATCH 액션과 섞으면 안 됩니다.

### 5.4 포인트, 결제, 출금

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/point` | `PointPage` | 사용 가능 포인트, 보관 금액, 총 잔액, 포인트 내역 필터 | `GET /api/v1/users/me/point`, `GET /api/v1/users/me/point/history` |
| `/point/charge` | `ChargePage` | 충전 금액 입력, 토스 결제 시작 | `POST /api/v1/payments/charge` |
| `/point/charge/callback` | `ChargeCallbackPage` | 토스 successUrl callback confirm | `POST /api/v1/payments/charge/confirm` |
| `/point/withdraw` | `WithdrawPage` | 계좌 정보 입력, 출금 신청/목록/취소 | `POST /api/v1/withdrawals`, `GET /api/v1/withdrawals`, `DELETE /api/v1/withdrawals/{id}` |

충전 응답은 Toss SDK 연동에 필요한 `merchantUid/orderId`, `amount`, `customerKey` 계열 필드가 필요합니다. 출금 신청은 `idempotencyKey`를 body에 포함합니다.

### 5.5 배송대행

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API/STOMP |
|---|---|---|---|
| `/delivery` | `DeliveryPage` | 배송 등록 모달, 모집중 목록, 내 배송 목록 | `POST /api/v1/deliveries`, `GET /api/v1/deliveries`, `GET /api/v1/deliveries/me` |
| `/delivery/:id/track` | `DeliveryTrackPage` | 단건 상세, 수락/픽업/전달/완료/취소 액션, 실시간 위치 지도 | `GET /api/v1/deliveries/{id}`, `PATCH /api/v1/deliveries/{id}/accept`, `PATCH /api/v1/deliveries/{id}/pickup`, `PATCH /api/v1/deliveries/{id}/deliver`, `PATCH /api/v1/deliveries/{id}/complete`, `PATCH /api/v1/deliveries/{id}/cancel`, `GET /api/v1/deliveries/{id}/location/last` |
| STOMP | `delivery/locationHooks.ts` | 라이더 위치 publish, 요청자 위치 subscribe | publish `/app/delivery/{id}/location`, subscribe `/topic/delivery/{id}/location` |

주소 입력은 카카오 주소 검색 컴포넌트를 사용합니다. 백엔드는 주소 텍스트, 위도/경도, 연락처, 배송 상태, rider/requester 권한을 일관되게 내려줘야 합니다.

### 5.6 마이페이지, 사용자, 차단

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/mypage` | `MyPage` | 내 프로필, 포인트 요약, 메뉴. 관리자는 통계 대시보드 lazy load | `GET /api/v1/users/me`, 관리자일 때 `GET /api/v1/admin/stats/dashboard/charts` |
| `/mypage/edit` | `ProfileEditPage` | 닉네임/프로필 이미지 수정, 소셜 계정 연결 | `PATCH /api/v1/users/me`, `POST /api/v1/files/presigned-url`, `POST /api/v1/auth/social-link/{provider}/preview`, `POST /api/v1/auth/social-link/confirm` |
| `/users/:id` | `UserProfilePage` | 공개 프로필, 받은 리뷰, 물품 | `GET /api/v1/users/{id}/profile`, `GET /api/v1/users/{id}/reviews`, `GET /api/v1/items?sellerId=` |
| `/mypage/blocks` | `BlockListPage` | 차단 목록, 차단 해제, 사용자 신고 | `GET /api/v1/blocks`, `DELETE /api/v1/blocks/{userId}`, `POST /api/v1/users/{userId}/report` |
| 전역/사용자 액션 | hooks | 차단 추가 | `POST /api/v1/blocks` |

공개 프로필 응답에는 이메일, 포인트 잔액, 인증 여부 같은 민감 정보가 포함되면 안 됩니다.

### 5.7 알림, 리뷰

| 라우트/위치 | 페이지/컴포넌트 | 주요 구성요소/기능 | 필요 API/STOMP |
|---|---|---|---|
| `/notifications` | `NotificationPage` | 알림 목록, 카테고리 탭, 읽음 처리, 전체 읽음 | `GET /api/v1/notifications`, `PATCH /api/v1/notifications/{id}/read`, `PATCH /api/v1/notifications/read-all`, `GET /api/v1/notifications/unread-count` |
| Header/dropdown | `NotificationDropdown` | 최근 알림, 링크 이동 | 위 알림 API, `/user/queue/notifications` |
| `/reviews` | `ReviewManagePage` | 작성 대기 리뷰, 받은 리뷰, 공개 여부 토글 | `GET /api/v1/reviews/pending`, `GET /api/v1/users/{id}/reviews`, `PATCH /api/v1/reviews/{id}/visibility` |
| `/reviews/write` | `ReviewWritePage` | 거래 완료 후 리뷰 작성 | `POST /api/v1/reviews` |

알림 응답의 `linkType`, `linkId`는 프론트 라우팅에 직접 사용됩니다. 현재 프론트는 `CHAT_ROOM`, `TRANSACTION`, `ESCROW`, `DELIVERY`, `ITEM`, `REVIEW`, `PAYMENT`, `INQUIRY` 계열을 처리합니다.

### 5.8 공지, 고객지원, 약관

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/notices` | `NoticePage` | 공지/이벤트 목록, 타입 필터 | `GET /api/v1/notices` |
| `/notices/:id` | `NoticeDetailPage` | 공지 상세, 조회수 증가 | `GET /api/v1/notices/{id}` |
| `/notices/write` | `NoticeWritePage` | 관리자 공지 작성 | `POST /api/v1/admin/notices`, `POST /api/v1/admin/files/presigned-url` |
| `/notices/:id/edit` | `NoticeWritePage` | 관리자 공지 수정 | `GET /api/v1/admin/notices/{id}`, `PATCH /api/v1/admin/notices/{id}` |
| `/support` | `SupportPage` | FAQ/QNA 목록, 1:1 문의 작성/내 문의 목록. 관리자일 때 문의 처리 뷰 포함 | `GET /api/v1/support/posts`, `POST /api/v1/support/inquiries`, `GET /api/v1/support/inquiries/me`, 관리자 `GET /api/v1/admin/inquiries` |
| `/mypage/inquiries/:id` | `MyInquiryDetailPage` | 내 문의 상세/삭제 | `GET /api/v1/support/inquiries/{id}`, `DELETE /api/v1/support/inquiries/{id}` |
| `/support/posts/new` | `AdminSupportPostFormPage` | 관리자 FAQ/QNA 작성 | `POST /api/v1/admin/support/posts` |
| `/support/posts/:id/edit` | `AdminSupportPostFormPage` | 관리자 FAQ/QNA 수정 | `GET /api/v1/support/posts/{id}`, `PUT /api/v1/admin/support/posts/{id}` |
| `/terms` | `TermsPage` | 정적 약관 페이지 | 없음 |

### 5.9 거래대행(Escrow)

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/escrow` | `EscrowHubPage` | 거래대행 메뉴, 관리자 수수료 설정 진입 | 사용자 메뉴, 관리자 설정 API |
| `/escrow/list` | `EscrowListPage` | 내 거래대행 신청 목록, 결제대기/진행중/사용중/반납중/취소대기/완료 상태 표시, 배송 추적 이동 | `GET /api/v1/escrow/applications/me` |
| `/escrow/list/:id` | `EscrowDetailPage` | 거래대행 상세, 취소, 수령확인, 인계확인, 대여 반납요청/반납확인, 취소 합의 | `GET /api/v1/escrow/applications/{id}`, `PATCH /api/v1/escrow/applications/{id}/cancel`, `POST /api/v1/escrow/applications/{id}/confirm-receipt`, `POST /api/v1/escrow/applications/{id}/confirm-handover`, `POST /api/v1/escrow/applications/{id}/request-return`, `POST /api/v1/escrow/applications/{id}/confirm-return`, `POST /api/v1/escrow/applications/{id}/cancel-request`, `POST /api/v1/escrow/applications/{id}/cancel-confirm`, `POST /api/v1/escrow/applications/{id}/cancel-withdraw` |
| `/escrow/apply` | `EscrowStartPage` | 외부 거래대행 링크 발급자 폼 | `POST /api/v1/escrow/links` |
| `/escrow/apply/link` | `EscrowLinkPage` | 발급된 링크 공유 화면 | state 기반, 별도 API 없음 |
| `/escrow/join/:linkId` | `EscrowInvitePage` | 초대 링크 확인 | `GET /api/v1/escrow/links/{linkToken}` |
| `/escrow/join/:linkId/form` | `EscrowApplicationPage` | 초대 수신자 신청 폼, 수수료 미리보기 | `GET /api/v1/escrow/links/{linkToken}`, `POST /api/v1/escrow/applications/preview`, `POST /api/v1/escrow/applications/by-link` |
| `/escrow/internal/new` | `EscrowInternalApplicationPage` | 채팅방 내부 판매자 draft 생성. 대여 거래대행은 `rentalEndAt` 입력 필수 | `POST /api/v1/escrow/applications/internal/draft`, `POST /api/v1/files/presigned-url` |
| `/escrow/:id/buyer-info` | `EscrowBuyerInfoPage` | 구매자 수령지/연락처 입력 | `GET /api/v1/escrow/applications/{id}`, `PATCH /api/v1/escrow/applications/{id}/buyer-info`, `POST /api/v1/escrow/applications/preview` |
| `/escrow/:id/pay` | `EscrowPayPage` | 본인 부담금 포인트 결제 | `GET /api/v1/escrow/applications/{id}/payment-preview`, `POST /api/v1/escrow/applications/{id}/pay` |
| `/escrow/join/:linkId/complete` | `EscrowCompletePage` | 신청 완료 안내 | 없음 또는 상세 조회 |

공통 구성요소로 `FeeCalculator`가 사용됩니다. 백엔드는 수수료/배송비 계산 결과와 fee snapshot을 프론트가 그대로 표시할 수 있도록 내려줘야 합니다.

거래대행 대여 lifecycle은 `결제완료 -> 진행중 -> 사용중 -> 반납중 -> 완료`입니다. `rentalEndAt`이 지난 뒤 구매자가 반납요청을 누르지 않으면 백엔드 스케줄러가 자동으로 `사용중 -> 반납중` 전환, 반납 배송 모집, 판매자 알림을 수행합니다.

| 상태 | 프론트 버튼 노출 기준 |
|---|---|
| `결제대기` | 결제 주체에게 결제 버튼 노출 |
| `진행중` | 구매자 `confirm-receipt`, 판매자 `confirm-handover` 노출 |
| `사용중` | 구매자 `request-return`, 양측 `cancel-request`, 취소 요청자는 `cancel-withdraw`, 상대방은 `cancel-confirm` 노출 |
| `반납중` | 판매자 `confirm-return` 노출 |
| `완료`, `취소` | 읽기 전용 |

거래대행 금액 흐름은 프론트 표시와 맞춰야 합니다. 결제 시 부담자 포인트가 차감되고, 대여는 구매자 보증금이 보관됩니다. 반납요청은 수동/자동 모두 구매자에게 반납 배송비를 차감합니다. 반납확인 시 판매자 물품 금액 정산, 왕복 라이더 수수료 지급, 구매자 보증금 반환이 처리됩니다.

### 5.10 관리자

| 라우트 | 페이지 | 주요 구성요소/기능 | 필요 API |
|---|---|---|---|
| `/admin/login` | `AdminLoginPage` | 관리자 로그인 | `POST /api/v1/auth/admin/login` |
| `/admin` | redirect | `/admin/dashboard`로 이동 | 없음 |
| `/admin/dashboard` | `AdminConsole` | 사이드바형 관리자 콘솔. 통계, 회원, 거래/물품, 운영, 콘텐츠, 설정 화면 inline 전환 | 아래 관리자 API 전체 |
| `/admin/users` | `AdminUserPage` | 전체 회원 검색/상태 관리 | `GET /api/v1/admin/users`, `GET /api/v1/admin/users/{id}`, `PATCH /api/v1/admin/users/{id}/block` |
| `/admin/users/today` | `AdminTodayUsersPage` | 오늘 신규 가입자 | `GET /api/v1/admin/users?createdAfter=&createdBefore=` |
| `/admin/users/withdrawn` | `AdminWithdrawnUsersPage` | 탈퇴 회원 | `GET /api/v1/admin/users?status=` |
| `/admin/trades` | `AdminMonthlyTradesPage` | 거래 검색/필터 | `GET /api/v1/admin/transactions` |
| `/admin/items` | `AdminItemPage` | 물품 검색, 상세, 강제 삭제 | `GET /api/v1/admin/items`, `GET /api/v1/admin/items/{id}`, `DELETE /api/v1/admin/items/{id}` |
| `/admin/reports` | `AdminReportPage` | 신고 목록/상세/처리 | `GET /api/v1/admin/reports`, `GET /api/v1/admin/reports/{id}`, `PATCH /api/v1/admin/reports/{id}` |
| `/admin/withdraws` | `AdminWithdrawPage` | 출금 승인/거절/완료 | `GET /api/v1/admin/withdrawals`, `PATCH /api/v1/admin/withdrawals/{id}` |
| `/admin/delivery` | `AdminDeliveryPage` | 배송 모니터링, 상태 통계 | `GET /api/v1/admin/deliveries`, `GET /api/v1/admin/deliveries/stats` |
| `/admin/notices` | `AdminNoticePage` | 공지 목록/게시/고정/삭제, 전체 알림 발송 | `GET/POST/PATCH/DELETE /api/v1/admin/notices`, `PATCH /pin`, `PATCH /publish`, `POST /api/v1/admin/notifications/broadcast` |
| `/admin/banners` | `AdminBannerPage` | 메인 배너 CRUD/활성 토글 | `GET/POST/PATCH/DELETE /api/v1/admin/banners`, `PATCH /active`, `POST /api/v1/admin/files/presigned-url` |
| `/admin/escrow-config` | `AdminEscrowConfigPage` | 거래대행 수수료 설정 | `GET /api/v1/admin/escrow/fee-settings`, `PATCH /api/v1/admin/escrow/fee-settings` |
| `/admin/support/:id` | `AdminInquiryDetailPage` | 문의 상세, 답변, 상태 변경, 삭제 | `GET /api/v1/admin/inquiries/{id}`, `PATCH /reply`, `PATCH /status`, `DELETE /api/v1/admin/inquiries/{id}` |
| `/admin/deposits` | `AdminDepositPage` | 현재 기본 레이아웃만 있음. 보증금은 거래대행 반납확인/취소합의에서 자동 반환되며 별도 관리자 수동 반환 API는 없음 | 구현 예정 |

`/admin/dashboard`는 실제 라우팅상 `AdminConsole`을 렌더링합니다. 직접 URL 진입을 위해 개별 `/admin/*` 라우트도 유지되어 있습니다.

## 6. 도메인별 프론트 모듈

| 도메인 | 주요 파일 | 역할 |
|---|---|---|
| auth | `features/auth/*` | 로그인, 회원가입, OAuth, 이메일 인증, 프로필 수정, auth store |
| item | `features/item/*` | 물품 목록/상세/등록/수정, 찜, 신고, 대여 가능 기간 |
| category | `features/category/*` | 카테고리 트리, 단건, 자동완성 |
| chat | `features/chat/*` | 채팅방, 메시지, 읽음, 나가기, chat store |
| trade | `features/trade/*` | 거래 생성, 내 거래 목록, 거래 상세, 상태 전이 |
| payment | `features/payment/*` | 포인트 충전, 잔액/내역, 출금 |
| delivery | `features/delivery/*` | 배송 생성/목록/상태 액션, 실시간 위치 |
| escrow | `features/escrow/*` | 거래대행 링크, 신청서, 상태, 수수료 설정 |
| notification | `features/notification/*` | 알림 목록, 읽음, unread count, 드롭다운 |
| review | `features/review/*` | 리뷰 작성, 작성 대기, 받은 리뷰, 공개 여부 |
| support | `features/support/*` | FAQ/QNA, 1:1 문의, 관리자 문의 처리 |
| admin | `features/admin/*` | 관리자 회원/배너/공지/신고/물품/배송/출금/통계 |
| user/block | `features/user`, `features/block` | 공개 프로필, 사용자 신고, 차단 목록 |

## 7. 이미지 업로드 구성

이미지는 공통 helper `shared/api/upload.ts`를 사용합니다.

1. 프론트가 파일 MIME/크기 검증
2. `POST /api/v1/files/presigned-url` 또는 `POST /api/v1/admin/files/presigned-url`
3. presigned URL로 S3 `PUT`
4. 도메인 API에 반환받은 `key` 또는 공개 URL 전달

| purpose | 사용처 | endpoint |
|---|---|---|
| `PROFILE` | 프로필 이미지 | `/api/v1/files/presigned-url` |
| `ITEM` | 물품 이미지 | `/api/v1/files/presigned-url` |
| `MESSAGE` | 채팅 첨부 | `/api/v1/files/presigned-url` |
| `SUPPORT` | 문의 첨부 | `/api/v1/files/presigned-url` |
| `ESCROW` | 거래대행 첨부 | `/api/v1/files/presigned-url` |
| `NOTICE` | 관리자 공지 이미지 | `/api/v1/admin/files/presigned-url` |
| `BANNER` | 관리자 배너 이미지 | `/api/v1/admin/files/presigned-url` |

프론트 검증 기준은 이미지 MIME `jpeg/jpg/png/webp/gif`, 단일 파일 5MB 이하, 한 번에 최대 10건입니다.

## 8. 백엔드 확인 필요 사항

| 항목 | 확인 내용 |
|---|---|
| 라우트 가드 보완 | 프론트에서 공개 라우트에 있는 관리자/거래대행 화면도 있으므로 백엔드 권한 검증 필수 |
| 알림 링크 규격 | `linkType`/`linkId`가 프론트 라우팅과 맞아야 함 |
| 거래 상태 enum | 사용자 거래 화면은 `채팅중`, `예약`, `인계완료`, `반납요청`, `거래완료`, `취소` 상태 전이를 기준으로 UI 분기 |
| 관리자 거래 검색 | `keyword`가 숫자면 거래/물품 ID, 문자열이면 닉네임/이메일 검색으로 기대 |
| 공개 프로필 | 이메일, 포인트, 인증 여부 등 민감 정보 미포함 필요 |
| STOMP 인증 | SockJS 쿠키 인증, AT 만료 시 refresh 후 재연결 가능해야 함 |
| 배송 위치 | 마지막 위치 REST fallback과 STOMP topic payload 스키마 일치 필요 |
| 거래대행 수수료 | preview 응답과 최종 신청 fee snapshot 필드 일치 필요 |
| 거래대행 상세 DTO | `rentalEndAt`, `depositAmount`, `usingStartedAt`, `returnRequestedAt`, `cancelRequestedBy` 등 상태 UI에 필요한 필드가 상세 응답에 노출되는지 확인 필요 |
| 거래대행 대여 액션 | `status`, `buyerId`, `sellerId` 기준으로 `request-return`, `confirm-return`, 취소 요청/철회/승인 버튼 노출 필요 |
| 구현 예정 화면 | `/transactions/:id`, `/admin/deposits`는 현재 플레이스홀더 |
