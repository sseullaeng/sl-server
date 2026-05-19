-- V35: escrow_applications.item_id — INTERNAL escrow 에서 source Item id 보존.
-- 라운드 14 — 대여 거래대행 종료 시 paired Transaction 의 tradeType / 보증금 / 대여 정보 정확 매핑용.
-- EXTERNAL escrow 는 Item 미연결 → NULL.

ALTER TABLE escrow_applications
    ADD COLUMN item_id BIGINT NULL
        COMMENT 'INTERNAL escrow 의 source Item id. paired Tx 생성 시 tradeType/보증금 lookup. EXTERNAL 은 NULL';

CREATE INDEX idx_escrow_applications_item_id
    ON escrow_applications (item_id);
