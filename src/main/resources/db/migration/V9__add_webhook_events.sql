-- =====================================================
-- V9: webhook_events (외부 PG webhook 멱등 저장)
-- =====================================================
-- 토스 webhook 의 eventId 를 UNIQUE 로 보관해 같은 이벤트 중복 처리 차단.
-- replay 공격 / PG 측 retry 두 케이스 모두 동일 이벤트면 한 번만 처리.
-- 설계 원칙:
--   - eventId 가 UNIQUE 라 INSERT 충돌 시 PG 가 즉시 거부 → 멱등 응답.
--   - rawPayload 는 감사용 보존 (마스킹은 application 책임 — 카드번호 등).
--   - source(toss/kakao 등) 분리해 PG 별 멱등 namespace 명시.
-- =====================================================

CREATE TABLE webhook_events (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    source        VARCHAR(20)  NOT NULL COMMENT 'TOSS | KAKAO | ...',
    event_id      VARCHAR(100) NOT NULL COMMENT 'PG 가 발급하는 이벤트 식별자',
    event_type    VARCHAR(50)  NOT NULL COMMENT 'PAYMENT.STATUS_CHANGED 등',
    payment_key   VARCHAR(200) NULL     COMMENT '연관 paymentKey (있으면)',
    raw_payload   TEXT         NOT NULL,
    received_at   DATETIME     NOT NULL,
    processed_at  DATETIME     NULL     COMMENT 'state 동기화 완료 시각',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_events_source_event (source, event_id),
    KEY idx_webhook_events_received_at (received_at DESC),
    KEY idx_webhook_events_payment_key (payment_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
