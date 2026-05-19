-- V33: 대여 거래대행 lifecycle 확장.
-- escrow 가 forward 배달 + 사용 + 반납 배달 양방향 lifecycle 주관 (라운드 14 결정 (A)).
-- 새 status: 사용중 (confirmReceipt 후 buyer 보유), 반납중 (buyer [반납요청] 후 return delivery 진행).
--
-- 컬럼:
--   rental_mode        — 대여 거래대행 여부. createInternal 시 item.tradeType=대여 면 TRUE.
--                        EXTERNAL escrow 는 항상 FALSE (외부 거래 = 판매/나눔만).
--   using_started_at   — 사용중 진입 시각. confirmReceipt 시점.
--   return_requested_at— 반납중 진입 시각. buyer [반납요청] 시점.

ALTER TABLE escrow_applications
    ADD COLUMN rental_mode TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '대여 거래대행 여부 — TRUE 면 사용중/반납중 단계 거침',
    ADD COLUMN using_started_at DATETIME NULL
        COMMENT '대여 한정 — confirmReceipt 시점 (사용중 진입). buyer 가 받은 순간',
    ADD COLUMN return_requested_at DATETIME NULL
        COMMENT '대여 한정 — buyer [반납요청] 시점 (반납중 진입). return delivery 모집 시작';

CREATE INDEX idx_escrow_applications_rental_mode
    ON escrow_applications (rental_mode, status);
