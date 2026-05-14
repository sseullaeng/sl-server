ALTER TABLE users
    ADD COLUMN overdue_debt_balance BIGINT NOT NULL DEFAULT 0
        COMMENT '연체로 인한 누적 채무 (Phase 2). 다음 충전/결제 시 우선 차감';

CREATE INDEX idx_users_overdue_debt ON users (overdue_debt_balance);
