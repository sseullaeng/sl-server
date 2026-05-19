-- V34: 대여 거래대행 양방향 배달 — delivery 의 forward / return 구분.
-- 같은 escrow_application_id 에 두 delivery (forward 픽업/배달, return 픽업/배달) 가 1:N 으로 묶임.
-- direction 컬럼으로 구분.

ALTER TABLE deliveries
    ADD COLUMN direction VARCHAR(10) NOT NULL DEFAULT 'FORWARD'
        COMMENT 'FORWARD = seller→buyer 배달, RETURN = buyer→seller 반환 (대여 한정)';

CREATE INDEX idx_deliveries_escrow_direction
    ON deliveries (escrow_application_id, direction);
