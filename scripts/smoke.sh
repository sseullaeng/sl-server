#!/usr/bin/env bash
# Smoke test 시나리오 — 회원가입(dev) → 카테고리 → 상품 → 채팅 → 메시지 → 알림 → 거래.
# 사용:
#   1) 다른 터미널에서 ./gradlew bootRun (Spring Boot 실행, http://localhost:8080)
#   2) 본 스크립트: ./scripts/smoke.sh

set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
JAR_BUYER=".smoke-buyer.cookies"
JAR_SELLER=".smoke-seller.cookies"
TS=$(date +%s)

trap 'rm -f "$JAR_BUYER" "$JAR_SELLER"' EXIT

cyan() { printf "\033[1;36m%s\033[0m\n" "$1"; }
red()  { printf "\033[1;31m%s\033[0m\n" "$1"; }
step() { cyan "▶ $1"; }

# ------------------------------------------------------------------
# CSRF 토큰을 cookie jar 에서 추출 — Spring 의 CookieCsrfTokenRepository 가 응답에 XSRF-TOKEN 쿠키 박음.
xsrf_of() {
  awk '/XSRF-TOKEN/{print $7}' "$1" | tail -1
}

# 인증 + CSRF 토큰 박는 헬퍼
api_post() {
  local jar="$1" url="$2" body="$3"
  curl -fsS -b "$jar" -c "$jar" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $(xsrf_of "$jar")" \
    -d "$body" "$url"
}

api_patch() {
  local jar="$1" url="$2" body="$3"
  curl -fsS -b "$jar" -c "$jar" -X PATCH \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $(xsrf_of "$jar")" \
    -d "$body" "$url"
}

api_post_no_csrf() {
  # dev login / auth 엔드포인트는 CSRF 면제
  local jar="$1" url="$2" body="$3"
  curl -fsS -c "$jar" -H "Content-Type: application/json" -d "$body" "$url"
}

# ------------------------------------------------------------------
step "0. 헬스체크"
curl -fsS "$BASE/actuator/health" | grep -q '"status":"UP"' && echo "  ✅ UP"

# ------------------------------------------------------------------
step "1. dev login — buyer / seller 시드 + JWT 쿠키 + CSRF 토큰 발급"
BUYER=$(api_post_no_csrf "$JAR_BUYER" "$BASE/api/v1/auth/dev/seed-and-login" "{\"nickname\":\"buyer-$TS\"}")
echo "  buyer: $BUYER"
BUYER_ID=$(echo "$BUYER" | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')

SELLER=$(api_post_no_csrf "$JAR_SELLER" "$BASE/api/v1/auth/dev/seed-and-login" "{\"nickname\":\"seller-$TS\"}")
echo "  seller: $SELLER"
SELLER_ID=$(echo "$SELLER" | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')

# CSRF 토큰을 cookie jar 에 박기 위해 한 번 GET — withHttpOnlyFalse 라 응답 쿠키로 옴
curl -sS -b "$JAR_BUYER" -c "$JAR_BUYER" "$BASE/api/v1/categories" > /dev/null
curl -sS -b "$JAR_SELLER" -c "$JAR_SELLER" "$BASE/api/v1/categories" > /dev/null

# ------------------------------------------------------------------
step "2. GET /categories (public, 트리)"
curl -fsS "$BASE/api/v1/categories" | head -c 200
echo "..."

# ------------------------------------------------------------------
step "3. POST /items — seller 가 상품 등록"
ITEM=$(api_post "$JAR_SELLER" "$BASE/api/v1/items" \
  "{\"title\":\"테스트 상품 $TS\",\"description\":\"smoke test\",\"price\":50000,\"tradeType\":\"판매\",\"region\":\"서울\"}")
echo "  $ITEM"
ITEM_ID=$(echo "$ITEM" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

# ------------------------------------------------------------------
step "4. GET /items?q=테스트 (검색)"
curl -fsS -G --data-urlencode "q=테스트" --data-urlencode "size=5" "$BASE/api/v1/items" | head -c 300
echo "..."

step "5. GET /items/{id} (상세, viewCount++)"
curl -fsS "$BASE/api/v1/items/$ITEM_ID" | head -c 300
echo "..."

# ------------------------------------------------------------------
step "6. POST /items/{id}/wishlist — buyer 가 찜"
api_post "$JAR_BUYER" "$BASE/api/v1/items/$ITEM_ID/wishlist" "{}" | head -c 100
echo

# ------------------------------------------------------------------
step "7. POST /chat-rooms — buyer 가 seller 와 채팅 시작"
ROOM=$(api_post "$JAR_BUYER" "$BASE/api/v1/chat-rooms" "{\"itemId\":$ITEM_ID}")
echo "  $ROOM"
ROOM_ID=$(echo "$ROOM" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

# ------------------------------------------------------------------
step "8. POST /chat-rooms/{id}/messages — buyer 메시지 발신"
api_post "$JAR_BUYER" "$BASE/api/v1/chat-rooms/$ROOM_ID/messages" \
  '{"content":"안녕하세요, 구매 가능한가요?"}' | head -c 300
echo "..."

step "8-1. seller 답장"
api_post "$JAR_SELLER" "$BASE/api/v1/chat-rooms/$ROOM_ID/messages" \
  '{"content":"네 가능합니다"}' | head -c 300
echo "..."

# ------------------------------------------------------------------
step "9. GET /chat-rooms/{id}/messages (커서 페이징)"
curl -fsS -b "$JAR_BUYER" "$BASE/api/v1/chat-rooms/$ROOM_ID/messages?size=10" | head -c 400
echo "..."

# ------------------------------------------------------------------
step "10. GET /notifications — seller / buyer 알림"
echo "  seller:"
curl -fsS -b "$JAR_SELLER" "$BASE/api/v1/notifications?size=5" | head -c 400
echo "..."
echo "  buyer:"
curl -fsS -b "$JAR_BUYER" "$BASE/api/v1/notifications?size=5" | head -c 400
echo "..."

# ------------------------------------------------------------------
step "11. POST /transactions — buyer 가 거래 시작"
TX=$(api_post "$JAR_BUYER" "$BASE/api/v1/transactions" "{\"itemId\":$ITEM_ID}")
echo "  $TX"
TX_ID=$(echo "$TX" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

step "12. PATCH /transactions/{id} — seller 가 예약"
api_patch "$JAR_SELLER" "$BASE/api/v1/transactions/$TX_ID" '{"action":"예약"}' | head -c 200
echo

step "12-1. GET /transactions/{id} — 상태 확인"
curl -fsS -b "$JAR_BUYER" "$BASE/api/v1/transactions/$TX_ID" | head -c 400
echo "..."

step "13. PATCH /transactions/{id} — 거래완료 시도 (Day 8 전엔 503 예상)"
curl -sS -b "$JAR_SELLER" -X PATCH \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $(xsrf_of "$JAR_SELLER")" \
  -d '{"action":"거래완료"}' \
  "$BASE/api/v1/transactions/$TX_ID" | head -c 300
echo "..."

# ------------------------------------------------------------------
step "14. PATCH /transactions/{id} — buyer 가 취소 (예약 → 판매중 복원)"
api_patch "$JAR_BUYER" "$BASE/api/v1/transactions/$TX_ID" \
  '{"action":"취소","cancelReason":"테스트"}' | head -c 200
echo

# ------------------------------------------------------------------
cyan "✅ Smoke test 완료"
echo "  buyer userId=$BUYER_ID  seller userId=$SELLER_ID"
echo "  itemId=$ITEM_ID  roomId=$ROOM_ID  txId=$TX_ID"
