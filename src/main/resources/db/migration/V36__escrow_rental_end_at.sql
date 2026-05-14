-- V36: escrow_applications.rental_end_at — 대여 거래대행 종료 예정 시각.
-- rentalMode=true 시 cmd 입력. 자동 [반납요청] 스케줄러 기준.

ALTER TABLE escrow_applications
    ADD COLUMN rental_end_at DATETIME NULL
        COMMENT '대여 거래대행 종료 예정 시각. rentalMode=true 시 cmd 입력. 자동 [반납요청] 스케줄러 기준.';

CREATE INDEX idx_escrow_applications_rental_end_at_status
    ON escrow_applications (rental_end_at, status);
