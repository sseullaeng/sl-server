-- V23: 채팅방 거래방식 분리 (PR-C 라운드 12)
--
-- 정책 (작업 목록 #채팅 #5):
--   * 구매자가 채팅 신청 시 구매(판매) / 대여 / 나눔 중 선택.
--   * 같은 (item, user1, user2) 쌍이라도 거래방식이 다르면 별도 채팅방.
--   * UNIQUE(item_id, user1_id, user2_id) → UNIQUE(item_id, user1_id, user2_id, trade_mode).
--
-- 기존 row 는 trade_mode='판매' 로 backfill (대다수가 판매 거래 가정).
-- 도메인은 itemId 기준 트레이드 타입과 매핑되어 검증 — 다른 mode 시도 시 도메인 검증.

ALTER TABLE chat_rooms
    ADD COLUMN trade_mode VARCHAR(10) NOT NULL DEFAULT '판매'
        COMMENT '채팅방 거래방식 — 판매 | 대여 | 나눔';

ALTER TABLE chat_rooms
    ADD CONSTRAINT chk_chat_rooms_trade_mode CHECK (trade_mode IN ('판매', '대여', '나눔'));

-- 신 UNIQUE 먼저 추가 — item_id 가 앞이라 fk_chat_rooms_item 의 인덱스를 자동 활용 (MySQL 1553 회피).
ALTER TABLE chat_rooms
    ADD CONSTRAINT uk_chat_rooms_item_users_mode UNIQUE (item_id, user1_id, user2_id, trade_mode);

-- 이제 구 UNIQUE drop 가능.
ALTER TABLE chat_rooms
    DROP INDEX uk_chat_rooms_item_users;
