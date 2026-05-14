package com.sseulang.domain.user.domain;

import com.sseulang.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", length = 20)
    private SocialProvider socialProvider;

    @Column(name = "social_id", length = 100)
    private String socialId;

    

    @Column(name = "password", length = 255)
    private String password;

    

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspend_days")
    private Integer suspendDays;

    

    @Column(name = "cumulative_suspend_days", nullable = false)
    private int cumulativeSuspendDays;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    

    @Column(name = "trust_score", precision = 3, scale = 2)
    private BigDecimal trustScore;

    
    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    
    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    

    @Column(name = "point_balance", nullable = false)
    private long pointBalance;

    

    @Column(name = "point_hold", nullable = false)
    private long pointHold;

    

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    

    public static User createSocialUser(
            SocialProvider provider,
            String providerId,
            Email email,
            String nickname,
            String profileImage
    ) {
        if (provider == null || provider == SocialProvider.LOCAL) {
            throw new IllegalArgumentException("소셜 provider 는 LOCAL 외여야 합니다");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId 는 필수입니다");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname 은 필수입니다");
        }

        User u = new User();
        u.email = email.value();
        u.nickname = nickname;
        u.profileImage = profileImage;
        u.socialProvider = provider;
        u.socialId = providerId;
        u.emailVerified = true;  
        u.blocked = false;
        u.deleted = false;
        return u;
    }

    

    public static User createLocalUser(Email email, String hashedPassword, String nickname) {
        if (email == null) {
            throw new IllegalArgumentException("email 은 필수입니다");
        }
        if (hashedPassword == null || hashedPassword.isBlank()) {
            throw new IllegalArgumentException("hashedPassword 는 필수입니다");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname 은 필수입니다");
        }

        User u = new User();
        u.email = email.value();
        u.nickname = nickname;
        u.socialProvider = SocialProvider.LOCAL;
        u.socialId = null;
        u.password = hashedPassword;
        u.emailVerified = false;  
        u.blocked = false;
        u.deleted = false;
        return u;
    }

    
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    

    public void updateProfile(String newProfileImage, String newNickname) {
        if (newNickname != null) {
            if (newNickname.isBlank() || newNickname.length() > 50) {
                throw new IllegalArgumentException("nickname 은 1~50자여야 합니다");
            }
            this.nickname = newNickname.trim();
        }
        if (newProfileImage != null) {
            
            this.profileImage = newProfileImage.isBlank() ? null : newProfileImage;
        }
    }

    

    public void linkSocial(SocialProvider provider, String socialId) {
        validateSocialLinkInput(provider, socialId);
        this.socialProvider = provider;
        this.socialId = socialId;
        // takeover — 미인증 LOCAL 점유 squatting 방어. password 무효화로 squat 한 공격자 차단.
        this.password = null;
        this.emailVerified = true;
    }

    // 사용자 명시 연결 — LOCAL 비밀번호 유지. 이후 LOCAL 로그인과 OAuth 로그인 모두 가능.
    public void addSocialLink(SocialProvider provider, String socialId) {
        validateSocialLinkInput(provider, socialId);
        if (this.socialProvider != null && this.socialProvider != SocialProvider.LOCAL) {
            throw new IllegalStateException("이미 소셜 계정과 연결된 사용자입니다");
        }
        this.socialProvider = provider;
        this.socialId = socialId;
    }

    private static void validateSocialLinkInput(SocialProvider provider, String socialId) {
        if (provider == null || provider == SocialProvider.LOCAL) {
            throw new IllegalArgumentException("소셜 provider 는 LOCAL 외여야 합니다");
        }
        if (socialId == null || socialId.isBlank()) {
            throw new IllegalArgumentException("socialId 는 필수입니다");
        }
    }

    
    public Email email() {
        return new Email(email);
    }

    
    public void block() {
        this.blocked = true;
    }

    
    public void unblock() {
        this.blocked = false;
    }

    
    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    

    public void suspend(int days, LocalDateTime now) {
        if (days < 1) {
            throw new IllegalArgumentException("days 는 1 이상이어야 합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        this.suspendedAt = now;
        this.suspendDays = days;
        this.cumulativeSuspendDays += days;
    }

    
    public boolean isAutoWithdrawTarget() {
        return !this.deleted && this.cumulativeSuspendDays >= 200;
    }

    
    public void markWithdrawn() {
        this.deleted = true;
    }

    
    public void unsuspend() {
        this.suspendedAt = null;
        this.suspendDays = null;
    }

    
    public boolean isSuspendedAt(LocalDateTime now) {
        if (suspendedAt == null || suspendDays == null || suspendDays <= 0) {
            return false;
        }
        LocalDateTime expiresAt = suspendedAt.plusDays(suspendDays);
        return now != null && now.isBefore(expiresAt);
    }

    

    public boolean isDormantAt(LocalDateTime now, int dormantThresholdDays) {
        if (now == null) return false;
        LocalDateTime base = lastLoginAt != null ? lastLoginAt : getCreatedAt();
        if (base == null) return false;
        return !base.plusDays(dormantThresholdDays).isAfter(now);  
    }

    
    public UserStatus derivedStatus(LocalDateTime now, int dormantThresholdDays) {
        if (deleted) return UserStatus.WITHDRAWN;
        if (blocked) return UserStatus.BLOCKED;
        if (isSuspendedAt(now)) return UserStatus.SUSPENDED;
        if (isDormantAt(now, dormantThresholdDays)) return UserStatus.DORMANT;
        return UserStatus.ACTIVE;
    }

    

    public boolean isAccessibleAt(LocalDateTime now) {
        return !blocked && !deleted && !isSuspendedAt(now);
    }
}
