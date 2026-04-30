-- =====================================================
-- V4: withdrawals 멱등성 키 추가 (게이트 1 보강)
-- =====================================================
-- 출금 신청 중복 차단 — 클라이언트가 보낸 idempotencyKey 와 user_id 의 조합으로 1건만 허용.
-- 동시 더블클릭 / 네트워크 재시도로 잔액이 두 번 차감되는 시나리오 방지.
-- 동일 (user_id, idempotency_key) 재요청은 ApplicationService 가 기존 withdrawal 을 그대로 반환.
-- =====================================================

ALTER TABLE withdrawals
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER user_id,
    ADD UNIQUE KEY uk_withdrawals_user_idem (user_id, idempotency_key);
