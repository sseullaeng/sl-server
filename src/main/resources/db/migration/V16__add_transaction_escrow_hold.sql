-- V16: Transaction escrow hold 정책 적용 (라운드 11)
--
-- 변경 요약 — 가이드 §5.1 거래 상태 머신 + §5.3 동시성 정합:
--   1) transactions.status enum 에 '인계완료' 추가 (예약 / 거래완료 사이)
--   2) transactions: handover_confirmed_at / receive_confirmed_at / escrow_hold_amount 컬럼 추가
--   3) users: point_hold 컬럼 추가 (거래 보관 중 잔액, point_balance 와 분리)
--
-- 기존 row 호환:
--   - 진행 중 status='예약' row 는 escrow_hold_amount default 0 — 옛 정책 그대로 종결
--   - 새 거래만 hold 흐름 진입 (TransactionApplicationService 가 0 → price 로 set)
--   - users.point_hold default 0 — 기존 row 영향 없음
--
-- ENUM ordinal: 기존 '거래완료' / '취소' row 는 이름 기반 매칭으로 안전.
--
-- !! prod 호환성 (게이트 2 W-2) !!
--   본 스크립트는 transactions / users / point_histories 3 테이블에 MODIFY COLUMN ENUM
--   (전체 테이블 재빌드) 을 포함한다. MySQL 8 InnoDB 기본 정책으로:
--   - 50만+ row 시 metadata lock 시간 + 디스크 재빌드 시간이 길어진다 (수십 초~수 분).
--   - 5/11 신규 운영 진입 시점에는 row 적어 영향 X — 본 마이그레이션 그대로 적용.
--   - row 100만+ 단계 진입 후엔 INPLACE / pt-online-schema-change 등 분리 마이그레이션 검토 필요.
--   R1 follow-up issue 등록 예정 (별도 마이그레이션 분할 가이드).

-- 1. transactions.status enum 확장 — '인계완료' 를 '예약' 과 '거래완료' 사이에 배치
ALTER TABLE transactions
    MODIFY COLUMN status ENUM('채팅중','예약','인계완료','거래완료','취소') NOT NULL DEFAULT '채팅중';

-- 2. 인계/인수 확인 시각 + escrow hold 금액
ALTER TABLE transactions
    ADD COLUMN handover_confirmed_at DATETIME NULL AFTER reserved_at,
    ADD COLUMN receive_confirmed_at  DATETIME NULL AFTER handover_confirmed_at,
    ADD COLUMN escrow_hold_amount    BIGINT NOT NULL DEFAULT 0 AFTER cancel_reason,
    ADD CONSTRAINT chk_transactions_escrow_hold_nonneg CHECK (escrow_hold_amount >= 0);

-- 3. users.point_hold — 거래 보관 잔액 (point_balance 와 분리, race-safe 원자 UPDATE)
ALTER TABLE users
    ADD COLUMN point_hold BIGINT NOT NULL DEFAULT 0 AFTER point_balance,
    ADD CONSTRAINT chk_users_point_hold_nonneg CHECK (point_hold >= 0);

-- 4. point_histories.point_type ENUM 확장 — 거래 hold 흐름 history 적재용
--    프론트 라벨 합의 (라운드 11 B-2):
--      거래보관 → "거래 #N 보관"        (예약 시 buyer balance -, hold +)
--      거래환불 → "거래 #N 취소 환불"   (취소 시 buyer balance +, hold -)
--    seller 정산은 기존 '판매정산' 활용 → "거래 #N 정산 수령"
--    buyer 의 hold 해제(인수확인)는 잔액 변화 X 라 history 미적재 (Aggregate 정책).
ALTER TABLE point_histories
    MODIFY COLUMN point_type ENUM(
        '충전','결제','판매정산','출금','환불','배달결제','배달정산',
        '거래보관','거래환불'
    ) NOT NULL;
