-- V18: 채팅방 soft hide (한쪽이 나가도 데이터/메타 유지, 본인만 안 보이게)
--
-- 정책:
--   * 본인이 나가면 user{N}_left_at = NOW(). 본인 채팅방 목록에서 제외 (visibility 필터).
--   * 상대방은 채팅방 안에서 opponentLeft=true 응답 받음 → 프론트가 입력창 disable + 시스템 메시지 렌더.
--   * 메시지 send 시 한쪽이 left 면 백엔드가 400 차단 (CHAT_ROOM_OPPONENT_LEFT / CHAT_FORBIDDEN).
--   * 데이터 삭제 X — 향후 분쟁/audit 대비 메시지·메타 보존.

ALTER TABLE chat_rooms
    ADD COLUMN user1_left_at DATETIME NULL COMMENT 'user1 이 채팅방 나간 시각 (soft hide)',
    ADD COLUMN user2_left_at DATETIME NULL COMMENT 'user2 가 채팅방 나간 시각 (soft hide)';
