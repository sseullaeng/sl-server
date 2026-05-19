-- V32: Transaction.deposit_original_percent — PERCENT 보증금 환산 시 원본 % 값 snapshot.
-- 청구서 영역에 "rentalPrice 의 30% = 36,000원" 같이 양쪽 표기 가능.
-- AMOUNT 보증금이거나 보증금 없는 거래는 NULL.
-- Item.deposit / Item.depositType 이 거래 후 변경돼도 거래 시점 snapshot 보존.

ALTER TABLE transactions
    ADD COLUMN deposit_original_percent INT NULL
    COMMENT 'PERCENT 보증금일 때 원본 % 값 (1~100). AMOUNT/없음은 NULL.';
