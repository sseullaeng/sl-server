-- V42: point_histories.point_type ENUM 확장 — 연체 시스템 (Codex PR1+PR2 누락 fix).
-- PointHistoryType 에 연체몰수, 연체채무상환 추가됐지만 컬럼 ENUM 미확장 → INSERT 시 'Data truncated'.

ALTER TABLE point_histories
    MODIFY COLUMN point_type ENUM(
        '충전','결제','판매정산','출금','환불',
        '배달결제','배달정산',
        '연체몰수','연체채무상환'
    ) NOT NULL;
