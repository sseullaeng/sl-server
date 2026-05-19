-- 거래대행 완료 시 paired Transaction 자동 생성 — 어드민/리뷰가 직거래와 동일 모델로 통합.
-- escrow_application_id 1:1 UNIQUE. EXTERNAL 거래대행은 Item 이 없으니 item_id nullable.

ALTER TABLE transactions
    MODIFY COLUMN item_id BIGINT NULL,
    ADD COLUMN escrow_application_id BIGINT NULL,
    ADD CONSTRAINT uk_tx_escrow UNIQUE (escrow_application_id),
    ADD CONSTRAINT fk_tx_escrow FOREIGN KEY (escrow_application_id) REFERENCES escrow_applications(id);
