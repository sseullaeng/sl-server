package com.sseulang.domain.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider 응답 JSON ↔ record 매핑 검증. 외부 API 호출 부분은 트리비얼 어댑터(가이드 §7.3)
 * 라 통합 테스트로 갈음하지만, 응답 스키마 매핑만큼은 단위로 닫는다.
 */
@JsonTest
class OAuthProviderJsonTest {

    @Autowired
    private ObjectMapper om;

    @Test
    @DisplayName("Kakao /v2/user/me 응답 파싱_id/email/verified flags/nickname/profile_image_url")
    void kakao_응답_파싱() throws Exception {
        String json = """
                {
                  "id": 1234567890,
                  "kakao_account": {
                    "email": "user@kakao.com",
                    "is_email_valid": true,
                    "is_email_verified": true,
                    "profile": {
                      "nickname": "쓸랭이",
                      "profile_image_url": "https://img.kakao/u.png"
                    }
                  }
                }
                """;

        KakaoOAuthProvider.KakaoUserResponse res =
                om.readValue(json, KakaoOAuthProvider.KakaoUserResponse.class);

        assertThat(res.id()).isEqualTo(1234567890L);
        assertThat(res.kakaoAccount().email()).isEqualTo("user@kakao.com");
        assertThat(res.kakaoAccount().isEmailValid()).isTrue();
        assertThat(res.kakaoAccount().isEmailVerified()).isTrue();
        assertThat(res.kakaoAccount().profile().nickname()).isEqualTo("쓸랭이");
        assertThat(res.kakaoAccount().profile().profileImageUrl()).isEqualTo("https://img.kakao/u.png");
    }

    @Test
    @DisplayName("Kakao 응답_verified flags 부재(null)_매핑 (provider 거부 회귀 보호)")
    void kakao_응답_verified_flags_null() throws Exception {
        String json = """
                {
                  "id": 1,
                  "kakao_account": {
                    "email": "x@y.com",
                    "profile": { "nickname": "n", "profile_image_url": null }
                  }
                }
                """;

        KakaoOAuthProvider.KakaoUserResponse res =
                om.readValue(json, KakaoOAuthProvider.KakaoUserResponse.class);

        assertThat(res.kakaoAccount().isEmailValid()).as("flag 부재 → null").isNull();
        assertThat(res.kakaoAccount().isEmailVerified()).as("flag 부재 → null").isNull();
    }

    @Test
    @DisplayName("Kakao 응답_is_email_verified=false_파싱 (provider 단계에서 OAuth 거부 회귀 보호)")
    void kakao_응답_email_verified_false() throws Exception {
        String json = """
                {
                  "id": 1,
                  "kakao_account": {
                    "email": "x@y.com",
                    "is_email_valid": true,
                    "is_email_verified": false,
                    "profile": { "nickname": "n", "profile_image_url": null }
                  }
                }
                """;

        KakaoOAuthProvider.KakaoUserResponse res =
                om.readValue(json, KakaoOAuthProvider.KakaoUserResponse.class);

        assertThat(res.kakaoAccount().isEmailValid()).isTrue();
        assertThat(res.kakaoAccount().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Kakao 응답_kakao_account 부재_null 매핑")
    void kakao_응답_kakao_account_부재() throws Exception {
        String json = """
                { "id": 9999 }
                """;

        KakaoOAuthProvider.KakaoUserResponse res =
                om.readValue(json, KakaoOAuthProvider.KakaoUserResponse.class);

        assertThat(res.id()).isEqualTo(9999L);
        assertThat(res.kakaoAccount()).isNull();
    }

    @Test
    @DisplayName("Google /oauth2/v3/userinfo 응답 파싱_sub/email/email_verified/name/picture")
    void google_응답_파싱() throws Exception {
        String json = """
                {
                  "sub": "1234567890",
                  "name": "쓸랭이",
                  "email": "user@gmail.com",
                  "email_verified": true,
                  "picture": "https://lh3.googleusercontent.com/x.jpg"
                }
                """;

        GoogleOAuthProvider.GoogleUserResponse res =
                om.readValue(json, GoogleOAuthProvider.GoogleUserResponse.class);

        assertThat(res.sub()).isEqualTo("1234567890");
        assertThat(res.name()).isEqualTo("쓸랭이");
        assertThat(res.email()).isEqualTo("user@gmail.com");
        assertThat(res.emailVerified()).isTrue();
        assertThat(res.picture()).isEqualTo("https://lh3.googleusercontent.com/x.jpg");
    }

    @Test
    @DisplayName("Google 응답_email_verified=false_매핑")
    void google_응답_email_verified_false() throws Exception {
        String json = """
                {
                  "sub": "1",
                  "name": "n",
                  "email": "x@y.com",
                  "email_verified": false
                }
                """;

        GoogleOAuthProvider.GoogleUserResponse res =
                om.readValue(json, GoogleOAuthProvider.GoogleUserResponse.class);

        assertThat(res.emailVerified()).isFalse();
        assertThat(res.picture()).isNull();
    }
}
