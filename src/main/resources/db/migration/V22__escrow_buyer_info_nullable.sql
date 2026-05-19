-- V22: 거래대행 입력 분리 본체 (PR-B-4 라운드 12)
--
-- 정책:
--   * 내부 draft 흐름 — 판매자가 본인 영역만 입력하여 application 생성 (status=정보입력대기).
--     이 시점에 구매자 영역 (delivery_*, receiver_phone) 은 미입력 → NULL 허용.
--   * 구매자가 buyer-info PATCH → buyerInfoFilled=true + fee 산정 + status=결제대기 전환.
--   * 외부 link 흐름은 변경 없음 — application 생성 시점에 모두 채워짐 (도메인 검증).
--
-- 변경 컬럼 (NOT NULL → NULL):
--   * delivery_address / delivery_lat / delivery_lng
--
-- weight / volume / fragility 는 판매자 영역 (물품 정보) 이라 NOT NULL 유지.
-- applied_* (fee snapshot) 컬럼은 양쪽 입력 완료 후 채워지므로 NULL 허용 필요.

ALTER TABLE escrow_applications
    MODIFY COLUMN delivery_address  VARCHAR(255)  NULL COMMENT '수령지 — 내부 draft 단계엔 NULL, buyer-info PATCH 시 입력',
    MODIFY COLUMN delivery_lat      DECIMAL(10,7) NULL COMMENT '수령지 위도 — buyer-info PATCH 시 입력',
    MODIFY COLUMN delivery_lng      DECIMAL(10,7) NULL COMMENT '수령지 경도 — buyer-info PATCH 시 입력';

-- fee snapshot — 양쪽 입력 완료 후 산정 (transitionToReadyForPayment 시점) → NULL 허용
ALTER TABLE escrow_applications
    MODIFY COLUMN applied_distance_km     DECIMAL(8,2) NULL,
    MODIFY COLUMN applied_delivery_fee    BIGINT       NULL,
    MODIFY COLUMN applied_commission_fee  BIGINT       NULL,
    MODIFY COLUMN applied_total_fee       BIGINT       NULL,
    MODIFY COLUMN applied_commission_rate DECIMAL(5,4) NULL;

-- share — fee snapshot 기반 산정. 동일하게 NULL 허용.
ALTER TABLE escrow_applications
    MODIFY COLUMN initiator_share BIGINT NULL,
    MODIFY COLUMN receiver_share  BIGINT NULL;
