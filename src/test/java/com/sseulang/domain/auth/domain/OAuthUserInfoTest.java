package com.sseulang.domain.auth.domain;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthUserInfoTest {

    private static final Email EMAIL = new Email("foo@example.com");

    @Test
    @DisplayName("정상 생성_필드 보존")
    void 정상생성() {
        OAuthUserInfo info = new OAuthUserInfo(SocialProvider.KAKAO, "k-1", EMAIL, "쓸랭이", "https://img/x.png");
        assertThat(info.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(info.providerId()).isEqualTo("k-1");
        assertThat(info.email()).isEqualTo(EMAIL);
        assertThat(info.nickname()).isEqualTo("쓸랭이");
        assertThat(info.profileImage()).isEqualTo("https://img/x.png");
    }

    @Test
    @DisplayName("profileImage null_허용")
    void profileImage_nullable() {
        OAuthUserInfo info = new OAuthUserInfo(SocialProvider.GOOGLE, "g-1", EMAIL, "n", null);
        assertThat(info.profileImage()).isNull();
    }

    @Test
    @DisplayName("LOCAL provider_거부")
    void local_거부() {
        assertThatThrownBy(() ->
                new OAuthUserInfo(SocialProvider.LOCAL, "id", EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("필수 필드 누락_거부")
    void 필수누락_거부() {
        assertThatThrownBy(() -> new OAuthUserInfo(null, "id", EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthUserInfo(SocialProvider.KAKAO, "", EMAIL, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthUserInfo(SocialProvider.KAKAO, "id", null, "n", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OAuthUserInfo(SocialProvider.KAKAO, "id", EMAIL, "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
