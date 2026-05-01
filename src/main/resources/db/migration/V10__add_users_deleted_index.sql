-- =====================================================
-- V10: users.is_deleted 단독 인덱스 (follow-up #37)
-- =====================================================
-- 배경: V5 의 (is_blocked, is_deleted) 복합 인덱스는 leftmost is_blocked 매칭이 가능한
-- countActive / countBlocked 에선 seek OK 지만, countDeleted (WHERE is_deleted=true) 는
-- 복합 인덱스에서 leftmost 가드 못 받아 covering scan 또는 skip scan 사용.
-- 단독 인덱스로 admin dashboard 의 countDeleted 응답 시간 안정화.
--
-- 카디널리티: is_deleted=true 가 매우 적은 비율 (정상 운영에서) 이라 인덱스 효율 우수.
-- =====================================================

CREATE INDEX idx_users_deleted ON users (is_deleted);
