package com.sseulang.domain.auth.infrastructure.oauth;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provider 진입 가드(null/blank token) 단위 테스트. 외부 API 호출 자체는 트리비얼 어댑터로 통합 테스트 영역.
 */
class OAuthProviderGuardTest {

    private final RestClient.Builder builder = RestClient.builder();

    @Test
    @DisplayName("Kakao supports_KAKAO")
    void kakao_supports() {
        KakaoOAuthProvider p = new KakaoOAuthProvider(builder, "test-client-id", "test-secret");
        assertThat(p.supports()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("Google supports_GOOGLE")
    void google_supports() {
        GoogleOAuthProvider p = new GoogleOAuthProvider(builder, "test-client-id", "test-secret");
        assertThat(p.supports()).isEqualTo(SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("Kakao verifyAndFetch null/blank 토큰_AUTH_OAUTH_FAILED")
    void kakao_blank_token() {
        KakaoOAuthProvider p = new KakaoOAuthProvider(builder, "test-client-id", "test-secret");

        assertThatThrownBy(() -> p.exchangeCodeAndFetch(null, "http://test/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);
        assertThatThrownBy(() -> p.exchangeCodeAndFetch("", "http://test/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);
        assertThatThrownBy(() -> p.exchangeCodeAndFetch("   ", "http://test/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);
    }

    @Test
    @DisplayName("Google verifyAndFetch null/blank 토큰_AUTH_OAUTH_FAILED")
    void google_blank_token() {
        GoogleOAuthProvider p = new GoogleOAuthProvider(builder, "test-client-id", "test-secret");

        assertThatThrownBy(() -> p.exchangeCodeAndFetch(null, "http://test/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);
        assertThatThrownBy(() -> p.exchangeCodeAndFetch("", "http://test/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_FAILED);
    }
}
