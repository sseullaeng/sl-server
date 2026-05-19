-- V24: 활동 정지 누적 일수 + 자동 탈퇴 (PR-F #8 라운드 12)
--
-- 정책:
--   * suspend() 호출 시마다 cumulative_suspend_days += days. 누적 200일 이상 → 자동 soft delete.
--   * 자동 탈퇴 = is_deleted=1 전환 + 안내 메일 발송. 데이터 보존.
--   * 배치 job (매일 새벽) 이 점검 후 처리. 또는 suspend 호출 직후 도메인 메서드가 자체 검사.
--
-- 기존 row 는 DEFAULT 0 — 이전 정지 이력은 합산 X (누적 시작 시점 V24 부터).

ALTER TABLE users
    ADD COLUMN cumulative_suspend_days INT NOT NULL DEFAULT 0
        COMMENT '활동 정지 누적 일수 — 200일 이상 자동 탈퇴 (PR-F #8 라운드 12)';

ALTER TABLE users
    ADD INDEX idx_users_cumulative_suspend (cumulative_suspend_days);
