-- =====================================================
-- V8: deliveries (배달대행 도메인)
-- =====================================================
-- 사용자(요청자) 가 배달 요청을 등록하고, 라이더가 수락 → 픽업 → 배송 → 정산 순으로 처리.
-- 정산 시 요청자 포인트 차감 + 라이더 포인트 적립 (PointHistory: DELIVERY_PAYMENT/INCOME).
-- 수락 race 차단은 ApplicationService 가 conditional UPDATE 로 처리 (Item 예약 패턴 동일).
-- =====================================================

-- point_histories.point_type ENUM 확장 — 배달 정산을 위한 새 type 추가.
-- 기존 V1 ENUM('충전','결제','판매정산','출금','환불') 에 '배달결제','배달정산' 추가.
ALTER TABLE point_histories
    MODIFY COLUMN point_type ENUM('충전','결제','판매정산','출금','환불','배달결제','배달정산') NOT NULL;

CREATE TABLE deliveries (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    requester_id        BIGINT       NOT NULL,
    rider_id            BIGINT       NULL,
    pickup_address      VARCHAR(255) NOT NULL,
    dropoff_address     VARCHAR(255) NOT NULL,
    item_description    VARCHAR(255) NOT NULL,
    fee                 BIGINT       NOT NULL,
    requested_deadline  DATETIME     NULL,
    memo                VARCHAR(500) NULL,
    status              VARCHAR(20)  NOT NULL,
    requested_at        DATETIME     NOT NULL,
    accepted_at         DATETIME     NULL,
    picked_up_at        DATETIME     NULL,
    delivered_at        DATETIME     NULL,
    completed_at        DATETIME     NULL,
    canceled_at         DATETIME     NULL,
    cancel_reason       VARCHAR(255) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_deliveries_status_requested (status, requested_at DESC),
    KEY idx_deliveries_requester (requester_id, requested_at DESC),
    KEY idx_deliveries_rider (rider_id, requested_at DESC),
    -- 정산 도메인 방어선 — 거래/출금 컨벤션 동일 (게이트 1 W-V8).
    -- FK: 운영상 user 는 soft-delete 패턴이라 실제 row 삭제 X. RESTRICT 로 두면 dangling 차단 +
    -- MySQL 8 제약(CHECK 에 ON DELETE SET NULL 컬럼 사용 불가, Err 3823) 회피.
    CONSTRAINT fk_deliveries_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_deliveries_rider     FOREIGN KEY (rider_id)     REFERENCES users(id) ON DELETE RESTRICT,
    -- fee 양수 + 본인 거래 차단 + status 화이트리스트.
    CONSTRAINT chk_deliveries_fee_positive   CHECK (fee > 0),
    CONSTRAINT chk_deliveries_not_self       CHECK (rider_id IS NULL OR rider_id <> requester_id),
    CONSTRAINT chk_deliveries_status_allowed CHECK (status IN ('모집중','수락','배송중','배송완료','정산완료','취소'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
