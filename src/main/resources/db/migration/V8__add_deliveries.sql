-- =====================================================
-- V8: deliveries (배달대행 도메인)
-- =====================================================
-- 사용자(요청자) 가 배달 요청을 등록하고, 라이더가 수락 → 픽업 → 배송 → 정산 순으로 처리.
-- 정산 시 요청자 포인트 차감 + 라이더 포인트 적립 (PointHistory: DELIVERY_PAYMENT/INCOME).
-- 수락 race 차단은 ApplicationService 가 conditional UPDATE 로 처리 (Item 예약 패턴 동일).
-- =====================================================

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
    KEY idx_deliveries_rider (rider_id, requested_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
