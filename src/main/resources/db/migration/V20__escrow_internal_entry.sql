-- V20: 거래대행 내부 신청 흐름 (PR-B-2)
--
-- 정책:
--   * 외부 link 흐름 (기존): initiator 가 link 생성 → receiver 가 form 제출 → application 생성
--   * 내부 chatRoom 흐름 (신규): 채팅방 안에서 판매자가 한 번에 양쪽 정보 입력 → application 생성. link 미사용.
--
-- 변경:
--   * link_id 를 nullable 로 (내부 흐름은 link 없음)
--   * entry_type 컬럼 추가 — INTERNAL | EXTERNAL 구분
--   * 기존 row 는 모두 EXTERNAL (link 흐름) — backfill
--
-- 입력 분리 (양쪽 본인 영역만 입력) 정책은 다음 라운드에서 별도 컬럼/플래그로 추가.

-- link_id NULL 허용
ALTER TABLE escrow_applications
    MODIFY COLUMN link_id BIGINT NULL COMMENT '외부 link 흐름의 link.id. 내부 chatRoom 흐름은 NULL.';

-- entry_type 추가
ALTER TABLE escrow_applications
    ADD COLUMN entry_type VARCHAR(10) NOT NULL DEFAULT 'EXTERNAL'
        COMMENT 'INTERNAL=채팅방 내 신청 / EXTERNAL=link 토큰 흐름';

-- 기존 row 는 모두 link 흐름이라 EXTERNAL. DEFAULT 로 자동 backfill.
-- 신규 INTERNAL row 는 ApplicationService.createInternalApplication 가 명시적으로 set.

ALTER TABLE escrow_applications
    ADD CONSTRAINT chk_escrow_applications_entry_type CHECK (entry_type IN ('INTERNAL', 'EXTERNAL'));

ALTER TABLE escrow_applications
    ADD INDEX idx_escrow_applications_entry_type (entry_type);
