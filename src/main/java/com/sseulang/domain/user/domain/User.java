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

/**
 * User Aggregate Root. V1 스키마 {@code users} 매핑.
 *
 * <p>본 PR(Day 3 OAuth) 범위에서 다루는 필드만 매핑. 나머지(point_balance / trust_score /
 * password / address ...) 는 후속 도메인 작업에서 필요 시 추가. JPA validate 는 entity 가 가진
 * 컬럼이 DB 에 있는지만 검사하므로 선택 매핑 가능.</p>
 *
 * <p>Setter 없음. 상태 변경은 명시된 비즈니스 메서드로만.</p>
 */
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

    /**
     * BCrypt 해싱된 비밀번호. LOCAL 가입 사용자에게만 채워짐 (소셜 가입은 null).
     * 평문 비밀번호 검증/해싱은 application layer 의 {@code LocalAuthService} 책임 — 도메인 layer 는
     * Spring Security 의존 X (CLAUDE.md §3.3).
     */
    @Column(name = "password", length = 255)
    private String password;

    /**
     * 이메일 소유 검증 여부. OAuth 가입자는 provider 가 검증한 이메일이라 자동 true,
     * LOCAL 가입자는 인증 메일 클릭 후 true. 민감 기능 (거래/결제/출금/Item 등록) 가드의 기준.
     * V6 마이그레이션에서 컬럼 추가 + OAuth 사용자 backfill.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /**
     * 마지막 로그인 시각 (V14). 휴면(dormant) 판정용 — 90일 이상 미접속이면 dormant.
     * LocalAuthService / OAuthLoginService 가 로그인 성공 시 갱신.
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    /**
     * 시한부 활동정지 (V14). is_blocked(영구) 와 별개. suspended_at + suspend_days 가 만료시각.
     * 만료 후엔 자동 해제 (status 계산 시점에 derive).
     */
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspend_days")
    private Integer suspendDays;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /**
     * 신뢰도(거래 후 받은 리뷰 평균). 가이드 §4.7 — 리뷰 작성 시점에 review_count + rating_sum 을
     * 단일 atomic UPDATE 로 누적해 race 안전 (Codex 게이트 2 보강).
     * 리뷰 0건이면 null ("신규" 표시용).
     */
    @Column(name = "trust_score", precision = 3, scale = 2)
    private BigDecimal trustScore;

    /** 누적 리뷰 수. trust_score 정합성을 위해 atomic 갱신 (V3 마이그). */
    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    /** 누적 별점 합계. trust_score = rating_sum / review_count (review_count > 0). */
    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    /**
     * 포인트 잔액(원). 가이드 §4.8 충전식 머니 — 충전/사용/적립/환불은 모두 atomic SQL UPDATE.
     * 본 필드 setter 없음 — UserRepository.creditPointBalance 등 atomic 메서드만이 갱신.
     */
    @Column(name = "point_balance", nullable = false)
    private long pointBalance;

    /**
     * JPA optimistic lock — V7 마이그레이션. LOCAL takeover 처럼 read-modify-write 흐름에서 두
     * 트랜잭션이 같은 user 를 동시 변경하면 OptimisticLockException 으로 한 쪽이 실패한다 (게이트 1).
     * 단순 atomic UPDATE (point_balance 등) 는 본 컬럼을 갱신하지 않는다 — JPA dirty-check 시점에만 동작.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * 소셜 가입 흐름의 정적 팩토리. (provider, providerId) 가 비어있을 수 없으며 LOCAL 은 거부.
     * 일반 회원가입 흐름은 별도 팩토리(예: {@code createLocalUser})로 분리.
     */
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
        u.emailVerified = true;  // OAuth provider 가 이메일 소유 검증 — 자동 verified
        u.blocked = false;
        u.deleted = false;
        return u;
    }

    /**
     * LOCAL 가입 정적 팩토리. 비밀번호는 호출자(LocalAuthService) 가 BCrypt 해싱한 결과만 받음 —
     * 도메인 layer 가 평문/해싱 전환 책임지지 않음 (Spring Security 의존성 회피, CLAUDE.md §3.3).
     */
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
        u.emailVerified = false;  // LOCAL 가입은 인증 메일 클릭 전까지 false
        u.blocked = false;
        u.deleted = false;
        return u;
    }

    /** LOCAL 가입 사용자만 비밀번호 보유. 소셜 가입은 null 반환. */
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    /** 이메일 인증 완료. 멱등 호출 가능. */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /**
     * 본인 프로필 partial update. null 인 필드는 변경하지 않음 (PATCH 의미).
     * 빈 문자열 nickname 은 거부.
     *
     * @param newProfileImage S3 GET URL (또는 key). null 이면 변경 X. 빈 문자열은 이미지 제거 의도로 허용 — null 로 설정.
     * @param newNickname 새 닉네임 (1~50자). null 이면 변경 X.
     */
    public void updateProfile(String newProfileImage, String newNickname) {
        if (newNickname != null) {
            if (newNickname.isBlank() || newNickname.length() > 50) {
                throw new IllegalArgumentException("nickname 은 1~50자여야 합니다");
            }
            this.nickname = newNickname.trim();
        }
        if (newProfileImage != null) {
            // 빈 문자열 → 이미지 제거 (null 로 저장)
            this.profileImage = newProfileImage.isBlank() ? null : newProfileImage;
        }
    }

    /**
     * OAuth takeover — 기존 LOCAL user 가 점유한 email 에 진짜 owner 가 OAuth 로 가입 시도.
     * 기존 user 의 password 무효화 + social 정보 추가 + verified=true. 공격자(LOCAL 가입자)는
     * 더 이상 비밀번호로 로그인 불가. 게이트 1: 이메일 선점 공격 무력화.
     *
     * <p>호출 전제: 같은 user 가 다른 OAuth provider 와 연결되지 않은 상태 — 호출자가 검증.</p>
     */
    public void linkSocial(SocialProvider provider, String socialId) {
        if (provider == null || provider == SocialProvider.LOCAL) {
            throw new IllegalArgumentException("소셜 provider 는 LOCAL 외여야 합니다");
        }
        if (socialId == null || socialId.isBlank()) {
            throw new IllegalArgumentException("socialId 는 필수입니다");
        }
        this.socialProvider = provider;
        this.socialId = socialId;
        // takeover — 기존 LOCAL 비밀번호 무효화 (공격자 차단)
        this.password = null;
        this.emailVerified = true;
    }

    /** 도메인 layer 외부에서 raw String 대신 VO 로 다루도록 의미적 wrapper. */
    public Email email() {
        return new Email(email);
    }

    /** 관리자 차단 — 이미 차단/삭제된 계정도 멱등 호출 가능 (true 보장). */
    public void block() {
        this.blocked = true;
    }

    /** 관리자 차단 해제 — 멱등 호출. */
    public void unblock() {
        this.blocked = false;
    }

    /** 로그인 성공 시점 기록. dormant 판정 기준. */
    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    /** 시한부 활동정지. days >= 1. now 부터 N일 동안. */
    public void suspend(int days, LocalDateTime now) {
        if (days < 1) {
            throw new IllegalArgumentException("days 는 1 이상이어야 합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        this.suspendedAt = now;
        this.suspendDays = days;
    }

    /** 활동정지 즉시 해제. */
    public void unsuspend() {
        this.suspendedAt = null;
        this.suspendDays = null;
    }

    /** 활동정지가 아직 유효 (만료 전). suspendedAt 없거나 만료됐으면 false. */
    public boolean isSuspendedAt(LocalDateTime now) {
        if (suspendedAt == null || suspendDays == null || suspendDays <= 0) {
            return false;
        }
        LocalDateTime expiresAt = suspendedAt.plusDays(suspendDays);
        return now != null && now.isBefore(expiresAt);
    }

    /** 휴면 — lastLoginAt 이 dormantThresholdDays 이상 과거. lastLoginAt 없으면 createdAt 기준. */
    public boolean isDormantAt(LocalDateTime now, int dormantThresholdDays) {
        if (now == null) return false;
        LocalDateTime base = lastLoginAt != null ? lastLoginAt : getCreatedAt();
        if (base == null) return false;
        return base.plusDays(dormantThresholdDays).isBefore(now);
    }

    /** Admin 응답용 status derive — WITHDRAWN > SUSPENDED > DORMANT > ACTIVE 우선순위. */
    public UserStatus derivedStatus(LocalDateTime now, int dormantThresholdDays) {
        if (deleted) return UserStatus.WITHDRAWN;
        if (isSuspendedAt(now)) return UserStatus.SUSPENDED;
        if (isDormantAt(now, dormantThresholdDays)) return UserStatus.DORMANT;
        return UserStatus.ACTIVE;
    }

    /**
     * 인증/민감 기능 진입 가능한 상태인지. blocked / deleted / suspended (만료 전) 모두 차단.
     * Codex 게이트 2 (round 9 hotfix) — 정지된 사용자가 기존 AT/RT 로 거래/결제/출금 진입하던 회귀 차단.
     */
    public boolean isAccessibleAt(LocalDateTime now) {
        return !blocked && !deleted && !isSuspendedAt(now);
    }
}
