package com.sseulang.domain.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
