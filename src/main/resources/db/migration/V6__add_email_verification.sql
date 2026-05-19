-- =====================================================
-- V6: 이메일 인증 + 계정 연동 인프라 (게이트 1 round 2 보강)
-- =====================================================
-- LOCAL 가입 시 이메일 소유 검증을 위한 인증 토큰. OAuth 가입자는 provider 가 검증한 이메일이라
-- 자동 verified=true. LOCAL 사용자는 인증 클릭 후 verified=true 가 되며, 그전엔 민감 기능
-- (거래/결제/출금/Item 등록) 이용 차단 (UserApplicationService.requireVerified).
-- =====================================================

ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER password;

-- 기존 OAuth 사용자 backfill — provider 가 이미 검증한 이메일이므로 verified=true 로 마킹.
-- 검증된 provider (KAKAO/GOOGLE) 만 명시 — DEV/LOCAL 또는 후속 추가될 검증 미보장 provider 가
-- 실수로 verified 처리되는 회귀 차단 (게이트 1 round 2). 카카오는 추가로 OAuth provider 응답 단계
-- 에서 is_email_valid + is_email_verified 를 강제 검증함 (KakaoOAuthProvider).
UPDATE users
   SET email_verified = TRUE
 WHERE social_provider IN ('KAKAO', 'GOOGLE');

CREATE TABLE email_verifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(64)  NOT NULL,
    purpose     VARCHAR(30)  NOT NULL DEFAULT 'SIGNUP',  -- SIGNUP / PASSWORD_RESET (후속)
    expires_at  DATETIME     NOT NULL,
    used_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_ver_token (token),
    KEY idx_email_ver_user (user_id),
    CONSTRAINT fk_email_ver_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
