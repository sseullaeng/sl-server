-- V38: 대여 거래대행 보증금 snapshot.
-- 라운드 14 PR8 — B1 결정 (a): escrow 결제 시 buyer 의 보증금만큼 point_hold,
-- confirmReturn 시 refundHold (point_hold → point_balance 복원).
--
-- snapshot 보존 이유: Item.deposit / depositType 이 거래 후 변경돼도 거래 시점 보증금 보존.

ALTER TABLE escrow_applications
    ADD COLUMN deposit_amount BIGINT NULL
        COMMENT 'rentalMode 한정. 결제 시 buyer point_hold 로 이동, confirmReturn 시 refund.',
    ADD COLUMN deposit_original_percent INT NULL
        COMMENT 'PERCENT 보증금일 때 원본 % snapshot. AMOUNT/없음은 NULL.';
