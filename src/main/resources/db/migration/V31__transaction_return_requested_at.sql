-- V31: 대여 거래 반납 흐름 — buyer 가 [반납] 요청 시각 기록.
-- 7일 후 seller 무회신이면 스케줄러가 자동 거래완료. UI 에 "반납 요청 후 N일 경과" 카운트다운.

ALTER TABLE transactions
    ADD COLUMN return_requested_at DATETIME NULL
    COMMENT '대여 한정 — buyer 가 [반납] 누른 시각. status=반납요청 진입 시 채워짐.';

CREATE INDEX idx_transactions_return_requested_at
    ON transactions (return_requested_at)
    COMMENT '반납요청 7일 자동 완료 스케줄러 조회 인덱스';
