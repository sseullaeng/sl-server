-- V19: 거래/거래대행 채팅방 가드 (#3.2 #3.3)
--
-- 정책:
--   * 거래 시작은 채팅방 안에서만 — chat_room_id 가 어떤 채팅방에서 시작됐는지 추적.
--   * 한 채팅방 = 1 active transaction 정책 강제 위해 (chat_room_id, status) 인덱스로 조회 비용 최소화.
--   * 거래대행 신청도 동일하게 chat_room_id 추적.
--
-- 컬럼은 NULL 허용:
--   * 기존 row (이전 라운드까지의 transaction/escrow_application) 는 chat_room_id 정보 없음 — legacy.
--   * 신규 row 부터는 도메인 검증 (TX_CHATROOM_REQUIRED / ESCROW_CHATROOM_REQUIRED) 으로 NOT NULL 효과.
--   * FK ON DELETE SET NULL — 채팅방 데이터 정리 시 거래/거래대행 row 보존, 채팅방 참조만 끊김.

-- transactions
ALTER TABLE transactions
    ADD COLUMN chat_room_id BIGINT NULL COMMENT '거래가 시작된 채팅방 (한 채팅방 = 1 active transaction)';

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_chat_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id) ON DELETE SET NULL;

ALTER TABLE transactions
    ADD INDEX idx_transactions_chat_room_status (chat_room_id, status);

-- escrow_applications
ALTER TABLE escrow_applications
    ADD COLUMN chat_room_id BIGINT NULL COMMENT '거래대행이 신청된 채팅방';

ALTER TABLE escrow_applications
    ADD CONSTRAINT fk_escrow_applications_chat_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id) ON DELETE SET NULL;

ALTER TABLE escrow_applications
    ADD INDEX idx_escrow_applications_chat_room (chat_room_id);
