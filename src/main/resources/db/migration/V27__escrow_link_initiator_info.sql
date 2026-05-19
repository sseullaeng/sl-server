-- 외부 link 분리 입력 흐름 (round 12 후속)
-- 발급자(initiator) 가 link 발급 시 본인 영역 미리 입력, 수신자는 자기 영역만 채워서 by-link 신청.
-- role 별 사용 컬럼이 달라 모두 nullable. 발급자 role 에 해당하지 않는 필드는 application 시 수신자가 채움.

ALTER TABLE escrow_links
    ADD COLUMN initiator_pickup_address VARCHAR(255) NULL,
    ADD COLUMN initiator_pickup_lat DECIMAL(10, 7) NULL,
    ADD COLUMN initiator_pickup_lng DECIMAL(10, 7) NULL,
    ADD COLUMN initiator_delivery_address VARCHAR(255) NULL,
    ADD COLUMN initiator_delivery_lat DECIMAL(10, 7) NULL,
    ADD COLUMN initiator_delivery_lng DECIMAL(10, 7) NULL,
    ADD COLUMN initiator_receiver_phone VARCHAR(20) NULL,
    ADD COLUMN initiator_item_price BIGINT NULL,
    ADD COLUMN initiator_item_description VARCHAR(500) NULL,
    ADD COLUMN initiator_weight VARCHAR(10) NULL,
    ADD COLUMN initiator_volume VARCHAR(5) NULL,
    ADD COLUMN initiator_fragility VARCHAR(5) NULL,
    ADD COLUMN initiator_delivery_notes VARCHAR(500) NULL,
    ADD COLUMN initiator_image_urls TEXT NULL;
