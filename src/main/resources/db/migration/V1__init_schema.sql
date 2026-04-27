-- =============================================================================
-- Sseulang 초기 스키마 (V1)
-- MySQL 8.x / InnoDB / utf8mb4
-- ngram parser 사용을 위해 my.cnf: ngram_token_size=2 (docker-compose에서 설정)
-- =============================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. users
-- =====================================================
CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(100) NOT NULL,
    password        VARCHAR(255) NULL,
    nickname        VARCHAR(50)  NOT NULL,
    phone           VARCHAR(20)  NULL,
    profile_image   VARCHAR(500) NULL,
    address         VARCHAR(255) NULL,
    address_detail  VARCHAR(255) NULL,
    point_balance   BIGINT       NOT NULL DEFAULT 0,
    trust_score     DECIMAL(3,2) NULL,
    review_count    INT          NOT NULL DEFAULT 0,
    social_provider VARCHAR(20)  NULL COMMENT 'LOCAL | KAKAO | GOOGLE',
    social_id       VARCHAR(100) NULL,
    is_blocked      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_social (social_provider, social_id),
    KEY idx_users_created_at (created_at),
    KEY idx_users_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. admins
-- =====================================================
CREATE TABLE admins (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admins_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. categories (self-ref)
-- =====================================================
CREATE TABLE categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT      NULL,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_categories_parent (parent_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. items
-- =====================================================
CREATE TABLE items (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id     BIGINT       NOT NULL,
    category_id   BIGINT       NULL,
    title         VARCHAR(200) NOT NULL,
    description   TEXT         NOT NULL,
    price         BIGINT       NOT NULL DEFAULT 0,
    deposit       BIGINT       NULL COMMENT '대여 시 보증금',
    rental_unit   VARCHAR(20)  NULL COMMENT '시간 | 일 | 주 | 월 (대여 시)',
    trade_type    ENUM('대여','판매','나눔') NOT NULL,
    status        ENUM('판매중','예약','거래완료','비공개','삭제') NOT NULL DEFAULT '판매중',
    region        VARCHAR(100) NULL,
    view_count    INT          NOT NULL DEFAULT 0,
    wishlist_count INT         NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_items_seller (seller_id),
    KEY idx_items_category (category_id),
    KEY idx_items_status_created (status, created_at DESC),
    KEY idx_items_trade_type (trade_type),
    KEY idx_items_created_at (created_at),
    FULLTEXT KEY ft_items_title_desc (title, description) WITH PARSER ngram,
    CONSTRAINT fk_items_seller   FOREIGN KEY (seller_id)   REFERENCES users(id)      ON DELETE RESTRICT,
    CONSTRAINT fk_items_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. item_images
-- =====================================================
CREATE TABLE item_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    item_id    BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    is_thumbnail BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_item_images_item (item_id),
    CONSTRAINT fk_item_images_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 6. item_hashtags
-- =====================================================
CREATE TABLE item_hashtags (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    item_id BIGINT      NOT NULL,
    tag     VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_hashtags_item_tag (item_id, tag),
    KEY idx_item_hashtags_tag (tag),
    CONSTRAINT fk_item_hashtags_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 7. wishlists
-- =====================================================
CREATE TABLE wishlists (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    item_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlists_user_item (user_id, item_id),
    KEY idx_wishlists_item (item_id),
    KEY idx_wishlists_user_created (user_id, created_at DESC),
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlists_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 8. transactions
-- =====================================================
CREATE TABLE transactions (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    item_id       BIGINT     NOT NULL,
    seller_id     BIGINT     NOT NULL,
    buyer_id      BIGINT     NOT NULL,
    trade_type    ENUM('대여','판매','나눔') NOT NULL,
    price         BIGINT     NOT NULL DEFAULT 0,
    deposit       BIGINT     NULL,
    rental_start  DATETIME   NULL,
    rental_end    DATETIME   NULL,
    status        ENUM('채팅중','예약','거래완료','취소') NOT NULL DEFAULT '채팅중',
    reserved_at   DATETIME   NULL,
    completed_at  DATETIME   NULL,
    canceled_at   DATETIME   NULL,
    cancel_reason VARCHAR(255) NULL,
    created_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_transactions_item (item_id),
    KEY idx_transactions_seller (seller_id),
    KEY idx_transactions_buyer (buyer_id),
    KEY idx_transactions_status (status),
    KEY idx_transactions_created_at (created_at),
    CONSTRAINT fk_transactions_item   FOREIGN KEY (item_id)   REFERENCES items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_buyer  FOREIGN KEY (buyer_id)  REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 9. payments (충전 + 거래 결제 통합)
-- =====================================================
CREATE TABLE payments (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '결제 주체',
    transaction_id BIGINT       NULL COMMENT '충전이면 NULL',
    payment_type   ENUM('충전','대여금','보증금','수수료','환불') NOT NULL,
    method         VARCHAR(30)  NULL COMMENT 'CARD | TRANSFER | VIRTUAL_ACCOUNT | POINT',
    amount         BIGINT       NOT NULL,
    status         ENUM('대기','진행중','완료','실패','환불진행중','환불완료','환불실패') NOT NULL DEFAULT '대기',
    payment_key    VARCHAR(200) NULL COMMENT '토스 결제 키',
    merchant_uid   VARCHAR(100) NOT NULL COMMENT '우리 측 주문 고유 ID',
    paid_at        DATETIME     NULL,
    canceled_at    DATETIME     NULL,
    fail_reason    VARCHAR(500) NULL,
    raw_response   TEXT         NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_merchant_uid (merchant_uid),
    KEY idx_payments_user (user_id),
    KEY idx_payments_transaction (transaction_id),
    KEY idx_payments_status (status),
    KEY idx_payments_created_at (created_at),
    CONSTRAINT fk_payments_user        FOREIGN KEY (user_id)        REFERENCES users(id)        ON DELETE RESTRICT,
    CONSTRAINT fk_payments_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 10. delivery_requests
-- =====================================================
CREATE TABLE delivery_requests (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_id  BIGINT        NOT NULL,
    requester_id    BIGINT        NOT NULL,
    pickup_address  VARCHAR(255)  NOT NULL,
    pickup_lat      DECIMAL(10,7) NULL,
    pickup_lng      DECIMAL(10,7) NULL,
    dropoff_address VARCHAR(255)  NOT NULL,
    dropoff_lat     DECIMAL(10,7) NULL,
    dropoff_lng     DECIMAL(10,7) NULL,
    driver_name     VARCHAR(50)   NULL,
    driver_phone    VARCHAR(20)   NULL,
    driver_lat      DECIMAL(10,7) NULL,
    driver_lng      DECIMAL(10,7) NULL,
    fee             BIGINT        NOT NULL DEFAULT 0,
    status          ENUM('신청','배달중','완료','취소') NOT NULL DEFAULT '신청',
    requested_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      DATETIME      NULL,
    completed_at    DATETIME      NULL,
    canceled_at     DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_delivery_transaction (transaction_id),
    KEY idx_delivery_requester (requester_id),
    KEY idx_delivery_status (status),
    CONSTRAINT fk_delivery_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_requester   FOREIGN KEY (requester_id)   REFERENCES users(id)        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 11. chat_rooms (메시지 본문은 MongoDB)
-- =====================================================
CREATE TABLE chat_rooms (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    item_id         BIGINT   NOT NULL,
    user1_id        BIGINT   NOT NULL,
    user2_id        BIGINT   NOT NULL,
    last_message    VARCHAR(500) NULL,
    last_message_at DATETIME NULL,
    user1_unread    INT      NOT NULL DEFAULT 0,
    user2_unread    INT      NOT NULL DEFAULT 0,
    is_active       BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_rooms_item_users (item_id, user1_id, user2_id),
    KEY idx_chat_rooms_user1 (user1_id, last_message_at DESC),
    KEY idx_chat_rooms_user2 (user2_id, last_message_at DESC),
    CONSTRAINT fk_chat_rooms_item  FOREIGN KEY (item_id)  REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_rooms_user1 FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_rooms_user2 FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_rooms_users CHECK (user1_id <> user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 12. user_blocks
-- =====================================================
CREATE TABLE user_blocks (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    blocker_id  BIGINT   NOT NULL,
    blocked_id  BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_blocks (blocker_id, blocked_id),
    KEY idx_user_blocks_blocked (blocked_id),
    CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_blocks_self CHECK (blocker_id <> blocked_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 13. user_reports
-- =====================================================
CREATE TABLE user_reports (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id  BIGINT       NOT NULL,
    reported_id  BIGINT       NULL,
    item_id      BIGINT       NULL,
    reason       VARCHAR(50)  NOT NULL,
    detail       TEXT         NULL,
    status       ENUM('접수','처리중','처리완료','반려') NOT NULL DEFAULT '접수',
    admin_id     BIGINT       NULL,
    admin_memo   VARCHAR(500) NULL,
    processed_at DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_reports_reporter (reporter_id),
    KEY idx_user_reports_reported (reported_id),
    KEY idx_user_reports_item (item_id),
    KEY idx_user_reports_status (status),
    KEY idx_user_reports_created_at (created_at),
    CONSTRAINT fk_user_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_user_reports_reported FOREIGN KEY (reported_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_user_reports_item     FOREIGN KEY (item_id)     REFERENCES items(id)  ON DELETE CASCADE,
    CONSTRAINT fk_user_reports_admin    FOREIGN KEY (admin_id)    REFERENCES admins(id) ON DELETE SET NULL,
    CONSTRAINT chk_user_reports_target CHECK (reported_id IS NOT NULL OR item_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 14. point_histories
-- =====================================================
CREATE TABLE point_histories (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    point_type     ENUM('충전','결제','판매정산','출금','환불') NOT NULL,
    amount         BIGINT      NOT NULL COMMENT '+ / - 부호 포함',
    balance_after  BIGINT      NOT NULL,
    reference_type VARCHAR(30) NULL COMMENT 'PAYMENT | TRANSACTION | WITHDRAWAL',
    reference_id   BIGINT      NULL,
    description    VARCHAR(255) NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_point_histories_user_created (user_id, created_at DESC),
    KEY idx_point_histories_reference (reference_type, reference_id),
    CONSTRAINT fk_point_histories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 15. notices
-- =====================================================
CREATE TABLE notices (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id    BIGINT       NULL,
    type        ENUM('공지','이벤트') NOT NULL DEFAULT '공지',
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,
    image_url   VARCHAR(500) NULL,
    is_pinned   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_published BOOLEAN     NOT NULL DEFAULT TRUE,
    view_count  INT          NOT NULL DEFAULT 0,
    starts_at   DATETIME     NULL,
    ends_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notices_pinned_created (is_pinned DESC, created_at DESC),
    KEY idx_notices_type (type),
    CONSTRAINT fk_notices_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 16. banners
-- =====================================================
CREATE TABLE banners (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id    BIGINT       NULL,
    title       VARCHAR(200) NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    link_url    VARCHAR(500) NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at   DATETIME     NULL,
    ends_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_banners_active_sort (is_active, sort_order),
    CONSTRAINT fk_banners_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 17. reviews (신규)
-- =====================================================
CREATE TABLE reviews (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT       NOT NULL,
    reviewer_id    BIGINT       NOT NULL,
    reviewee_id    BIGINT       NOT NULL,
    rating         TINYINT      NOT NULL,
    comment        VARCHAR(500) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_tx_reviewer (transaction_id, reviewer_id),
    KEY idx_reviews_reviewee_created (reviewee_id, created_at DESC),
    KEY idx_reviews_reviewer (reviewer_id),
    CONSTRAINT fk_reviews_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer    FOREIGN KEY (reviewer_id)    REFERENCES users(id)        ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewee    FOREIGN KEY (reviewee_id)    REFERENCES users(id)        ON DELETE CASCADE,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_self  CHECK (reviewer_id <> reviewee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 18. withdrawals (신규)
-- =====================================================
CREATE TABLE withdrawals (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    amount         BIGINT       NOT NULL,
    bank_name      VARCHAR(50)  NOT NULL,
    account_number VARCHAR(50)  NOT NULL,
    account_holder VARCHAR(50)  NOT NULL,
    status         ENUM('신청','승인','거부','완료','취소') NOT NULL DEFAULT '신청',
    admin_id       BIGINT       NULL,
    admin_memo     VARCHAR(500) NULL,
    requested_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at   DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_withdrawals_user_created (user_id, created_at DESC),
    KEY idx_withdrawals_status (status),
    CONSTRAINT fk_withdrawals_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_withdrawals_admin FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL,
    CONSTRAINT chk_withdrawals_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 19. trust_score_histories (신규, 선택)
-- =====================================================
CREATE TABLE trust_score_histories (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    review_id    BIGINT       NULL,
    score_before DECIMAL(3,2) NULL,
    score_after  DECIMAL(3,2) NULL,
    review_count INT          NOT NULL DEFAULT 0,
    description  VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trust_history_user_created (user_id, created_at DESC),
    CONSTRAINT fk_trust_history_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_trust_history_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
