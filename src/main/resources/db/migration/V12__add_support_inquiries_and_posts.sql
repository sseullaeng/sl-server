-- =============================================================================
-- V12 — 고객지원 도메인 (Inquiry 1:1 문의 + SupportPost FAQ/QNA)
--
-- Inquiry  : 사용자가 작성하는 1:1 비공개 문의. 관리자가 답변.
-- SupportPost : 관리자가 작성하는 공개 게시글. FAQ / QNA 두 종류 (postType).
--
-- image_urls 는 JSON 컬럼 — 최대 5장, 단건 ≤ 500자. 별도 child 테이블 안 만든 이유:
--   * 순서 변경 / 단건 삭제 같은 부분 조작 X (작성·수정 시 통째로 갱신)
--   * 카드 UI 도 thumbnail join 같은 게 필요 없음
--   * row 5개 추가 join 비용 회피
-- 필요 시 후속 마이그레이션으로 child 테이블 분리 가능.
-- =============================================================================

CREATE TABLE inquiries (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    category      VARCHAR(20)  NOT NULL,        -- 계정/거래/결제/배송/기타 (한글 enum)
    title         VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    email         VARCHAR(255) NOT NULL,        -- 답변 받을 이메일 (작성 시점 본인 이메일 스냅샷)
    status        VARCHAR(20)  NOT NULL,        -- PENDING / PROCESSING / DONE
    image_urls    JSON         NULL,            -- ["https://...", ...] (최대 5)
    admin_reply   TEXT         NULL,
    replied_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_inquiries_user_created (user_id, created_at DESC),
    KEY idx_inquiries_status_created (status, created_at DESC)
);

CREATE TABLE support_posts (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_type     VARCHAR(10)  NOT NULL,        -- FAQ / QNA
    category      VARCHAR(20)  NOT NULL,        -- 계정/거래/결제/배송/기타
    question      VARCHAR(500) NOT NULL,
    answer        TEXT         NOT NULL,
    image_urls    JSON         NULL,
    admin_id      BIGINT       NULL,            -- 작성자 추적용 (운영 감사). soft FK (RESTRICT 강제 X)
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_support_posts_type_category (post_type, category, created_at DESC),
    KEY idx_support_posts_created (created_at DESC)
);
