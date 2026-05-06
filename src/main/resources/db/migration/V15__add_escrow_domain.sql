-- =============================================================================
-- V15 — 거래대행 (Escrow) 도메인 신설
--
-- 5개 변경:
--   1) escrow_links              : 신청자가 link 생성 → 수신자 join
--   2) escrow_applications       : 폼 제출 + 결제 추적 + 상태머신 + snapshot
--   3) escrow_fee_settings       : 운영 수수료 정책 (singleton row)
--   4) ALTER deliveries          : escrow_application_id FK (배달대행과 연결)
--   5) ALTER users               : is_rider 플래그 (라이더 권한)
--
-- 12 결정 사항 통합 (게이트 1 보안/결제/정산 영역).
-- =============================================================================

-- =============================================================================
-- 1) escrow_links — 신청자가 생성, 수신자가 join 하는 invite link
--    UUID token + 24h expiry + receiver_id race-safe (atomic UPDATE).
-- =============================================================================
CREATE TABLE escrow_links (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    link_token        VARCHAR(36)  NOT NULL              COMMENT 'UUID v4 — 외부 노출 식별자',
    initiator_id      BIGINT       NOT NULL,
    receiver_id       BIGINT       NULL                  COMMENT '첫 폼 제출 시 atomic UPDATE 로 확정',
    initiator_role    VARCHAR(10)  NOT NULL              COMMENT 'buyer | seller — 수신자는 반대',
    fee_payer         VARCHAR(10)  NOT NULL              COMMENT 'buyer | seller | both',
    trade_mode        VARCHAR(20)  NOT NULL              COMMENT 'INTERNAL (itemPrice escrow) | EXTERNAL (배달만)',
    status            VARCHAR(20)  NOT NULL              COMMENT '대기 | 완료 | 만료 | 취소',
    expires_at        DATETIME     NOT NULL              COMMENT 'created + APP_ESCROW_LINK_EXPIRY_HOURS (default 24h)',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_escrow_links_token       (link_token),
    KEY        idx_escrow_links_initiator  (initiator_id, created_at DESC),
    KEY        idx_escrow_links_receiver   (receiver_id, created_at DESC),
    KEY        idx_escrow_links_expires    (status, expires_at),
    CONSTRAINT fk_escrow_links_initiator   FOREIGN KEY (initiator_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_links_receiver    FOREIGN KEY (receiver_id)  REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_escrow_links_role        CHECK (initiator_role IN ('buyer','seller')),
    CONSTRAINT chk_escrow_links_fee_payer   CHECK (fee_payer IN ('buyer','seller','both')),
    CONSTRAINT chk_escrow_links_trade_mode  CHECK (trade_mode IN ('INTERNAL','EXTERNAL')),
    CONSTRAINT chk_escrow_links_status      CHECK (status IN ('대기','완료','만료','취소')),
    CONSTRAINT chk_escrow_links_not_self    CHECK (receiver_id IS NULL OR receiver_id <> initiator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
-- 2) escrow_applications — 폼 제출 후 단일 row (결제/정산/취소 라이프사이클)
--    snapshot 컬럼 (applied_*) — settings 변경 시 진행 중 신청 보호.
--    initiator_share/receiver_share — feePayer 별 부담 산정 결과.
-- =============================================================================
CREATE TABLE escrow_applications (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    link_id                     BIGINT        NOT NULL,
    -- 당사자 (denormalized for indexing)
    initiator_id                BIGINT        NOT NULL,
    receiver_id                 BIGINT        NOT NULL,
    buyer_id                    BIGINT        NOT NULL              COMMENT 'role 매핑 후 확정',
    seller_id                   BIGINT        NOT NULL,
    trade_mode                  VARCHAR(20)   NOT NULL              COMMENT 'INTERNAL | EXTERNAL',
    fee_payer                   VARCHAR(10)   NOT NULL              COMMENT 'buyer | seller | both',
    -- 폼 — 물품
    item_price                  BIGINT        NOT NULL DEFAULT 0    COMMENT 'INTERNAL>0, EXTERNAL=0',
    item_description            VARCHAR(500)  NOT NULL,
    -- 폼 — 픽업/배송 좌표
    pickup_address              VARCHAR(255)  NOT NULL,
    pickup_lat                  DECIMAL(10,7) NOT NULL,
    pickup_lng                  DECIMAL(10,7) NOT NULL,
    delivery_address            VARCHAR(255)  NOT NULL,
    delivery_lat                DECIMAL(10,7) NOT NULL,
    delivery_lng                DECIMAL(10,7) NOT NULL,
    -- 폼 — 옵션
    weight                      VARCHAR(10)   NOT NULL              COMMENT 'lt1 | 1to3 | 3to5 | 5to10 | gt10',
    volume                      VARCHAR(5)    NOT NULL              COMMENT 's | m | l',
    fragility                   VARCHAR(5)    NOT NULL              COMMENT 'f1 ~ f5',
    delivery_notes              VARCHAR(500)  NULL,
    -- snapshot (결정 #9, #10, #12) — 운영 settings 변경 무관 lock
    applied_distance_km         DECIMAL(8,2)  NOT NULL,
    applied_delivery_fee        BIGINT        NOT NULL,
    applied_commission_fee      BIGINT        NOT NULL              COMMENT 'INTERNAL 만 > 0',
    applied_total_fee           BIGINT        NOT NULL,
    applied_commission_rate     DECIMAL(5,4)  NOT NULL,
    -- 결제 추적 (결정 #5, G2 양쪽 별도 결제)
    initiator_share             BIGINT        NOT NULL DEFAULT 0    COMMENT '신청자 부담분 (feePayer 산정 결과)',
    receiver_share              BIGINT        NOT NULL DEFAULT 0    COMMENT '수신자 부담분',
    initiator_paid_at           DATETIME      NULL,
    receiver_paid_at            DATETIME      NULL,
    payment_due_at              DATETIME      NULL                  COMMENT '첫 결제 시점 + 24h timeout',
    -- 상태머신
    status                      VARCHAR(20)   NOT NULL              COMMENT '결제대기 | 결제완료 | 진행중 | 완료 | 취소',
    cancel_reason               VARCHAR(500)  NULL,
    cancelled_by                BIGINT        NULL                  COMMENT '취소 트리거 user_id (귀책자)',
    -- 정산 (Mode B — buyer 수령 확인 수동)
    receipt_confirmed_at        DATETIME      NULL,
    settled_at                  DATETIME      NULL,
    -- 이미지 (S3 key 또는 url, JSON list)
    image_urls                  TEXT          NULL,
    created_at                  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_escrow_applications_link        (link_id),
    KEY        idx_escrow_applications_buyer      (buyer_id, created_at DESC),
    KEY        idx_escrow_applications_seller     (seller_id, created_at DESC),
    KEY        idx_escrow_applications_status     (status, created_at DESC),
    KEY        idx_escrow_applications_due        (payment_due_at),
    CONSTRAINT fk_escrow_applications_link        FOREIGN KEY (link_id)      REFERENCES escrow_links(id) ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_applications_initiator   FOREIGN KEY (initiator_id) REFERENCES users(id)         ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_applications_receiver    FOREIGN KEY (receiver_id)  REFERENCES users(id)         ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_applications_buyer       FOREIGN KEY (buyer_id)     REFERENCES users(id)         ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_applications_seller      FOREIGN KEY (seller_id)    REFERENCES users(id)         ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_applications_cancelled   FOREIGN KEY (cancelled_by) REFERENCES users(id)         ON DELETE RESTRICT,
    CONSTRAINT chk_escrow_applications_status     CHECK (status IN ('결제대기','결제완료','진행중','완료','취소')),
    CONSTRAINT chk_escrow_applications_trade_mode CHECK (trade_mode IN ('INTERNAL','EXTERNAL')),
    CONSTRAINT chk_escrow_applications_fee_payer  CHECK (fee_payer IN ('buyer','seller','both')),
    CONSTRAINT chk_escrow_applications_buyer_seller_diff CHECK (buyer_id <> seller_id),
    -- INTERNAL 은 itemPrice > 0, EXTERNAL 은 itemPrice = 0 (commission 도 EXTERNAL 은 0)
    CONSTRAINT chk_escrow_applications_item_price CHECK (
        (trade_mode = 'EXTERNAL' AND item_price = 0 AND applied_commission_fee = 0) OR
        (trade_mode = 'INTERNAL' AND item_price > 0)
    ),
    CONSTRAINT chk_escrow_applications_total_fee_positive CHECK (applied_total_fee > 0),
    CONSTRAINT chk_escrow_applications_share CHECK (initiator_share >= 0 AND receiver_share >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
-- 3) escrow_fee_settings — 운영 수수료 정책 (singleton row, id=1)
--    프론트 calcFees 와 동일 11 fields (결정 #9 — CC3).
--    multiplier (weight/volume/fragility) 는 양쪽 코드 상수 (운영 변경 X — follow-up).
-- =============================================================================
CREATE TABLE escrow_fee_settings (
    id                          BIGINT        NOT NULL              COMMENT 'always = 1 (singleton)',
    commission_rate             DECIMAL(5,4)  NOT NULL              COMMENT '0.0500 = 5%',
    fuel_price_per_l            BIGINT        NOT NULL              COMMENT '실시간 유류비 (원/L)',
    base_fuel_price             BIGINT        NOT NULL              COMMENT '기준 유류비 (원/L) — 차이로 km_rate 보정',
    base_delivery_fee           BIGINT        NOT NULL              COMMENT '일반 차량 기본 배달비 (원)',
    base_km_rate                BIGINT        NOT NULL              COMMENT '일반 km당 추가 (원)',
    fuel_efficiency             DECIMAL(5,2)  NOT NULL              COMMENT '일반 km/L',
    min_delivery_fee            BIGINT        NOT NULL              COMMENT '일반 최저 배달비 (원)',
    truck_base_delivery_fee     BIGINT        NOT NULL              COMMENT '용달 (5kg+) 기본 배달비',
    truck_base_km_rate          BIGINT        NOT NULL              COMMENT '용달 km당 추가',
    truck_fuel_efficiency       DECIMAL(5,2)  NOT NULL              COMMENT '용달 km/L',
    truck_min_delivery_fee      BIGINT        NOT NULL              COMMENT '용달 최저 배달비',
    updated_at                  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by                  BIGINT        NULL                  COMMENT 'admin id (마지막 변경자)',
    PRIMARY KEY (id),
    CONSTRAINT chk_escrow_fee_settings_singleton  CHECK (id = 1),
    CONSTRAINT chk_escrow_fee_settings_commission CHECK (commission_rate >= 0 AND commission_rate <= 1),
    CONSTRAINT chk_escrow_fee_settings_positive   CHECK (
        fuel_price_per_l > 0 AND base_fuel_price > 0 AND
        base_delivery_fee > 0 AND base_km_rate > 0 AND fuel_efficiency > 0 AND min_delivery_fee > 0 AND
        truck_base_delivery_fee > 0 AND truck_base_km_rate > 0 AND truck_fuel_efficiency > 0 AND truck_min_delivery_fee > 0
    ),
    CONSTRAINT fk_escrow_fee_settings_admin       FOREIGN KEY (updated_by) REFERENCES admins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기본값 — 프론트 mock 과 동일 (5% commission, fuelPrice 1650, etc.)
INSERT INTO escrow_fee_settings (
    id, commission_rate, fuel_price_per_l, base_fuel_price,
    base_delivery_fee, base_km_rate, fuel_efficiency, min_delivery_fee,
    truck_base_delivery_fee, truck_base_km_rate, truck_fuel_efficiency, truck_min_delivery_fee,
    updated_at, updated_by
) VALUES (
    1, 0.0500, 1650, 1650,
    1500, 500, 25.00, 3000,
    5000, 1200, 10.00, 15000,
    NOW(), NULL
);


-- =============================================================================
-- 4) ALTER deliveries — escrow_application_id FK
--    NULL = 일반 배달대행, NOT NULL = 거래대행 연결 (DomainEvent 자동 생성).
-- =============================================================================
ALTER TABLE deliveries
    ADD COLUMN escrow_application_id BIGINT NULL AFTER memo,
    ADD CONSTRAINT fk_deliveries_escrow FOREIGN KEY (escrow_application_id)
        REFERENCES escrow_applications(id) ON DELETE RESTRICT;

-- 한 application 당 delivery 1개 (자동 매칭 멱등성).
CREATE UNIQUE INDEX uk_deliveries_escrow ON deliveries (escrow_application_id);


-- =============================================================================
-- 5) ALTER users — is_rider 플래그 (관리자 부여)
-- =============================================================================
ALTER TABLE users
    ADD COLUMN is_rider BOOLEAN NOT NULL DEFAULT FALSE AFTER is_deleted;

CREATE INDEX idx_users_is_rider ON users (is_rider);


-- =============================================================================
-- 6) ALTER payments — escrow_application_id (Escrow 결제 추적)
--    payments 가 transaction_id, escrow_application_id 둘 중 하나로 분기.
-- =============================================================================
ALTER TABLE payments
    ADD COLUMN escrow_application_id BIGINT NULL AFTER transaction_id,
    ADD CONSTRAINT fk_payments_escrow FOREIGN KEY (escrow_application_id)
        REFERENCES escrow_applications(id) ON DELETE RESTRICT;

CREATE INDEX idx_payments_escrow ON payments (escrow_application_id);
