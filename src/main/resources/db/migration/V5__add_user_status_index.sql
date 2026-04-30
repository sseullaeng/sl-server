-- =====================================================
-- V5: users (is_blocked, is_deleted) 복합 인덱스 (게이트 2 보강)
-- =====================================================
-- 관리자 dashboard 의 countBlocked / countDeleted / countActive 가 사용자 수 증가에 따라
-- full table scan 되는 문제 회피. dashboard 쿼리는 두 컬럼 모두 WHERE 절에 등장하므로
-- 단일 boolean 인덱스보다 복합 인덱스가 효율적.
-- =====================================================

ALTER TABLE users
    ADD KEY idx_users_blocked_deleted (is_blocked, is_deleted);
