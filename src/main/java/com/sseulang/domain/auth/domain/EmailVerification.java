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

    

    public void markUsed(LocalDateTime now) {
        if (usedAt != null) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
        }
        
        if (now == null || !now.isBefore(expiresAt)) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_EXPIRED);
        }
        this.usedAt = now;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        
        return now != null && !now.isBefore(expiresAt);
    }
}
