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
}
