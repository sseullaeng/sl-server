-- =============================================================================
-- V14 — Admin 회원 카드/필터용 컬럼
--
-- last_login_at  : 휴면(dormant) 판정용. 90일 이상 미접속이면 dormant=true.
-- suspended_at   : 시한부 활동정지 시작 시각 (is_blocked 영구와 별개)
-- suspend_days   : 정지 기간 (일). suspended_at + suspend_days 가 만료 시각.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN last_login_at  DATETIME NULL AFTER email_verified,
    ADD COLUMN suspended_at   DATETIME NULL AFTER is_blocked,
    ADD COLUMN suspend_days   INT      NULL AFTER suspended_at;

-- 휴면 필터 / 활동정지 필터 인덱스. (idx_users_created_at 은 V1 에 이미 존재)
CREATE INDEX idx_users_last_login   ON users (last_login_at);
CREATE INDEX idx_users_suspended_at ON users (suspended_at);
