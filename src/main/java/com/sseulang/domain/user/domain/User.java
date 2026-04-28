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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

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
        u.blocked = false;
        u.deleted = false;
        return u;
    }

    /** 도메인 layer 외부에서 raw String 대신 VO 로 다루도록 의미적 wrapper. */
    public Email email() {
        return new Email(email);
    }
}
