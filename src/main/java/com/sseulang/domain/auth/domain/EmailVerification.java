package com.sseulang.domain.auth.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이메일 인증 토큰. 가입 직후 발급되고 사용자가 메일의 링크 클릭 시 verifyToken 으로 소진.
 * 한 user 가 여러 토큰 보유 가능 (재발송 시) — 검증 시 가장 최근 미사용 토큰 매칭.
 */
@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseEntity {

    private static final int TOKEN_LENGTH_MIN = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, length = 64, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public static EmailVerification issue(Long userId, String token, VerificationPurpose purpose, LocalDateTime expiresAt) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 는 양수여야 합니다");
        }
        if (token == null || token.length() < TOKEN_LENGTH_MIN) {
            // 짧은 토큰은 brute-force 위험 — 32자 이상 강제 (UUID + extra entropy 조합).
            throw new IllegalArgumentException("token 은 " + TOKEN_LENGTH_MIN + "자 이상이어야 합니다");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose 는 필수입니다");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt 은 필수입니다");
        }
        EmailVerification v = new EmailVerification();
        v.userId = userId;
        v.token = token;
        v.purpose = purpose;
        v.expiresAt = expiresAt;
        return v;
    }

    /**
     * 토큰 사용 처리. 만료 / 이미 사용된 토큰은 거부 (멱등 X — 재사용 차단).
     */
    public void markUsed(LocalDateTime now) {
        if (usedAt != null) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
        }
        // 만료 경계는 닫힘 — expiresAt 와 같은 시각도 만료된 것으로 간주 (Codex 게이트 1 round 2 nit).
        if (now == null || !now.isBefore(expiresAt)) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_EXPIRED);
        }
        this.usedAt = now;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        // expiresAt 시각 자체가 만료 경계 — !isBefore 로 닫힘 처리.
        return now != null && !now.isBefore(expiresAt);
    }
}
