-- V21: 거래대행 양 당사자 입력 분리 (PR-B-4 라운드 12)
--
-- 정책:
--   * 내부 흐름 (INTERNAL): 판매자가 신청 시 본인 영역만 입력 → status = 정보입력대기
--     구매자가 본인 영역 PATCH → buyerInfoFilled=true → fee 산정 + status = 결제대기
--   * 외부 흐름 (EXTERNAL): 기존 link 흐름 그대로 — receiver 가 form 제출 시 모든 정보 입력 + 즉시 결제대기.
--
-- 컬럼:
--   * seller_info_filled / buyer_info_filled 신규 (양쪽 입력 완료 추적)
--   * receiver_phone 신규 (구매자 영역 — 수령자 연락처)
--   * 기존 delivery_*, weight/volume/fragility 등은 내부 흐름의 draft 단계엔 미입력 가능 → nullable
--     (외부 흐름은 application 생성 시점에 모두 채워지므로 영향 없음)
--
-- 상태머신 신규: 정보입력대기 (status CHECK 갱신)

-- status CHECK 갱신
ALTER TABLE escrow_applications
    DROP CHECK chk_escrow_applications_status;
ALTER TABLE escrow_applications
    ADD CONSTRAINT chk_escrow_applications_status
        CHECK (status IN ('정보입력대기', '결제대기', '결제완료', '진행중', '완료', '취소'));

-- 플래그 + 신규 컬럼
ALTER TABLE escrow_applications
    ADD COLUMN seller_info_filled BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '판매자 영역(출발지/물품) 입력 완료. 외부 흐름은 항상 TRUE — DEFAULT TRUE 로 backfill.',
    ADD COLUMN buyer_info_filled  BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '구매자 영역(수령지/연락처) 입력 완료. 외부 흐름은 항상 TRUE.',
    ADD COLUMN receiver_phone     VARCHAR(20) NULL
        COMMENT '수령자 연락처 (구매자 영역 — 내부 흐름은 buyer-info PATCH 시 입력).';

-- 신규 INTERNAL 신청 (PR-B-4 이후) 부터 buyer_info_filled=FALSE 로 시작 (도메인 로직).
-- 기존 row 는 모두 외부 흐름 + 양쪽 입력 완료 상태라 DEFAULT TRUE backfill OK.

-- delivery_*, weight/volume/fragility 는 향후 PR 에서 nullable 검토 (현재 외부 흐름이 NOT NULL 강제 하므로 일단 그대로).
