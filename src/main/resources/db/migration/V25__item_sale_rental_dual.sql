-- V25: 물품 판매/대여 이중 등록 (라운드 12 PR-D 잔여)
--
-- 정책:
--   * 한 Item 이 판매 / 대여 / 나눔 중 복수 모드 동시 등록 가능.
--   * 새 컬럼 sale_price / rental_price / trade_types 가 authoritative.
--   * 기존 price / trade_type 은 single-mode 호환용으로 유지 (DEPRECATED — 추후 V30 대 제거).
--   * trade_types 는 ',' separated VARCHAR — JPA 측에서 Set<TradeType> 으로 변환.
--
-- backfill: 기존 row 는 단일 모드 — sale_price/rental_price/trade_types 가 trade_type 기반으로 채워짐.

ALTER TABLE items
    ADD COLUMN sale_price   BIGINT NULL COMMENT '판매가 (판매 모드 시 필수)',
    ADD COLUMN rental_price BIGINT NULL COMMENT '대여가 (대여 모드 시 필수, rental_unit 당)',
    ADD COLUMN trade_types  VARCHAR(64) NULL COMMENT '거래 모드 set (예: "판매,대여")';

UPDATE items
   SET sale_price   = CASE WHEN trade_type = '판매' THEN price ELSE NULL END,
       rental_price = CASE WHEN trade_type = '대여' THEN price ELSE NULL END,
       trade_types  = trade_type
 WHERE trade_types IS NULL;

ALTER TABLE items
    MODIFY COLUMN trade_types VARCHAR(64) NOT NULL;

CREATE INDEX idx_items_trade_types ON items (trade_types);
