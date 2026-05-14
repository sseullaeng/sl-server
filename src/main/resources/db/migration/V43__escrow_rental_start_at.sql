-- V43: 대여 거래대행 신청 시 rentalStartAt 추가.
-- 기존 rentalEndAt 만 있던 상태 → 시작/종료 둘 다 보존하여 백엔드가 itemPrice 자동 산정.
--
-- itemPrice = item.rentalPrice × ceil(duration / item.rentalUnit)
-- 위변조 차단 + FE 가 보낸 임의 가격 거부.

ALTER TABLE escrow_applications
    ADD COLUMN rental_start_at DATETIME NULL
        COMMENT '대여 시작 예정 시각 — rentalEndAt 과 함께 duration 계산. 대여 거래대행만 사용.';

CREATE INDEX idx_escrow_applications_rental_period
    ON escrow_applications (rental_start_at, rental_end_at);
