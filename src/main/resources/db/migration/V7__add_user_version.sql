-- =====================================================
-- V7: users.version 컬럼 — JPA optimistic lock (게이트 1 round 2 보강)
-- =====================================================
-- LOCAL takeover 같은 read-modify-write 흐름에서 두 OAuth provider 가 동시 takeover 시도 시
-- last-commit-wins 로 한 쪽 social 정보가 사라지는 race 방지. JPA @Version 으로 update 시점에
-- where version = :loaded 조건 추가 → 동시 변경 충돌은 OptimisticLockException.
-- =====================================================

ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER point_balance;
