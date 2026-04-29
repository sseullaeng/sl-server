-- =============================================================================
-- 신뢰도(trust_score) 동시성 안전을 위한 누적 합계 컬럼 추가 — Codex 게이트 2 (Day 5 후반).
-- 단일 SELECT AVG + UPDATE 는 REPEATABLE_READ read view 경계로 race 발생 가능.
-- review_count(V1 부터 존재) + rating_sum(본 마이그) 누적 + 원자 UPDATE 로 정합성 확보.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN rating_sum INT NOT NULL DEFAULT 0 AFTER review_count;
