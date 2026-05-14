-- V37: 대여 거래대행 사용중 단계 양 당사자 합의 취소.
-- 라운드 14 PR7 — 사용중 status 에서 한쪽이 [취소 요청] → 다른쪽 [취소 동의] 시 취소 확정.
-- 단순화: 새 status 없이 플래그 사용. 사용중 + cancel_requested_by != null = "취소 대기" UI 상태.
--
-- 흐름:
--   사용중 → requestCancelDuringUsing → 사용중 + cancel_requested_by/at 채움
--   사용중(요청자 != 호출자) → confirmCancelDuringUsing → 취소 status
--   사용중 → withdrawCancelRequest (요청자만) → cancel_requested_by/at null 로 복원

ALTER TABLE escrow_applications
    ADD COLUMN cancel_requested_by BIGINT NULL
        COMMENT '사용중 단계 취소 요청자 user id. 다른 참여자가 confirm 시 취소 확정',
    ADD COLUMN cancel_requested_at DATETIME NULL
        COMMENT '취소 요청 시각';

CREATE INDEX idx_escrow_applications_cancel_requested
    ON escrow_applications (status, cancel_requested_by);
