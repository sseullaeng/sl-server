-- 거래대행 판매자 [물품 인계] 확인 시점 — UX 용 audit 타임스탬프 (정산 영향 X)
ALTER TABLE escrow_applications
    ADD COLUMN handover_confirmed_by_seller_at TIMESTAMP NULL;
