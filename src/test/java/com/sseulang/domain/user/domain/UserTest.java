package com.sseulang.domain.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Email EMAIL = new Email("foo@example.com");

    @Test
    @DisplayName("createSocialUser 정상_필드 채워지고 blocked/deleted=false")
    void createSocialUser_정상() {
        User u = User.createSocialUser(SocialProvider.KAKAO, "kakao-12345", EMAIL, "쓸랭이", "https://img.kakao/u.png");

        assertThat(u.email()).isEqualTo(EMAIL);
        assertThat(u.getNickname()).isEqualTo("쓸랭이");
        assertThat(u.getProfileImage()).isEqualTo("https://img.kakao/u.png");
        assertThat(u.getSocialProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(u.getSocialId()).isEqualTo("kakao-12345");
        assertThat(u.isBlocked()).isFalse();
        assertThat(u.isDeleted()).isFalse();
        assertThat(u.getPhone()).isNull();
    }

    @Test
    @DisplayName("createSocialUser LOCAL_provider 거부")
    void createSocialUser_LOCAL_거부() {
        assertThatThrownBy(() ->
                User.createSocialUser(SocialProvider.LOCAL, "id", EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createSocialUser null provider_거부")
    void createSocialUser_null_provider_거부() {
        assertThatThrownBy(() ->
                User.createSocialUser(null, "id", EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createSocialUser 빈 providerId_거부")
    void createSocialUser_빈_providerId_거부() {
        assertThatThrownBy(() ->
                User.createSocialUser(SocialProvider.GOOGLE, "", EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                User.createSocialUser(SocialProvider.GOOGLE, null, EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createSocialUser 빈 nickname_거부")
    void createSocialUser_빈_nickname_거부() {
        assertThatThrownBy(() ->
                User.createSocialUser(SocialProvider.GOOGLE, "id", EMAIL, "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                User.createSocialUser(SocialProvider.GOOGLE, "id", EMAIL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createLocalUser 정상_provider=LOCAL + hashedPassword 저장 + hasPassword=true")
    void createLocalUser_정상() {
        User u = User.createLocalUser(EMAIL, "$2a$10$hashed", "로컬");

        assertThat(u.getEmail()).isEqualTo(EMAIL.value());
        assertThat(u.getNickname()).isEqualTo("로컬");
        assertThat(u.getSocialProvider()).isEqualTo(SocialProvider.LOCAL);
        assertThat(u.getSocialId()).isNull();
        assertThat(u.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(u.hasPassword()).isTrue();
        assertThat(u.isBlocked()).isFalse();
        assertThat(u.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("createLocalUser invalid 인자 거부")
    void createLocalUser_invalid() {
        assertThatThrownBy(() -> User.createLocalUser(null, "$2a$10$h", "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.createLocalUser(EMAIL, "", "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.createLocalUser(EMAIL, null, "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.createLocalUser(EMAIL, "$2a$10$h", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hasPassword_소셜 가입자는 false")
    void hasPassword_소셜() {
        User social = User.createSocialUser(SocialProvider.KAKAO, "k-1", EMAIL, "kakao", null);
        assertThat(social.hasPassword()).isFalse();
    }

    @Test
    @DisplayName("suspend_여러번 호출시 cumulativeSuspendDays 합산")
    void suspend_누적_합산() {
        User u = User.createSocialUser(SocialProvider.KAKAO, "k-2", EMAIL, "n", null);
        LocalDateTime now = LocalDateTime.now();
        u.suspend(7, now);
        u.suspend(30, now.plusDays(10));
        u.suspend(60, now.plusDays(50));
        assertThat(u.getCumulativeSuspendDays()).isEqualTo(97);
    }

    @Test
    @DisplayName("isAutoWithdrawTarget_누적 200 미만은 false")
    void isAutoWithdrawTarget_미달() {
        User u = User.createSocialUser(SocialProvider.KAKAO, "k-3", EMAIL, "n", null);
        u.suspend(150, LocalDateTime.now());
        assertThat(u.isAutoWithdrawTarget()).isFalse();
    }

    @Test
    @DisplayName("isAutoWithdrawTarget_누적 200 도달은 true (경계 포함)")
    void isAutoWithdrawTarget_경계() {
        User u = User.createSocialUser(SocialProvider.KAKAO, "k-4", EMAIL, "n", null);
        u.suspend(100, LocalDateTime.now());
        u.suspend(100, LocalDateTime.now().plusDays(50));
        assertThat(u.getCumulativeSuspendDays()).isEqualTo(200);
        assertThat(u.isAutoWithdrawTarget()).isTrue();
    }

    @Test
    @DisplayName("isAutoWithdrawTarget_이미 deleted 이면 false")
    void isAutoWithdrawTarget_이미_탈퇴() {
        User u = User.createSocialUser(SocialProvider.KAKAO, "k-5", EMAIL, "n", null);
        u.suspend(250, LocalDateTime.now());
        u.markAutoWithdrawn();
        assertThat(u.isDeleted()).isTrue();
        assertThat(u.isAutoWithdrawTarget()).isFalse();
    }
}
