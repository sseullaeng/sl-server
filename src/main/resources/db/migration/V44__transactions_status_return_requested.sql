-- V44: transactions.status ENUM 확장 — 대여 반납요청 (라운드 14 B-6 누락 fix).
-- TransactionStatus 에 반납요청 추가됐지만 컬럼 ENUM 미확장 → PATCH /transactions/{id} {action:반납요청} 시
-- Hibernate batch update 단계에서 'Data truncated for column status' (1265, 01000).

ALTER TABLE transactions
    MODIFY COLUMN status ENUM(
        '채팅중','예약','인계완료','반납요청','거래완료','취소'
    ) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '채팅중';
