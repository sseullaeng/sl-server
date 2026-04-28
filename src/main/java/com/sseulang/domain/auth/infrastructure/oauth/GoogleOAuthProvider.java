package com.sseulang.domain.auth.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글 access_token 검증 + 사용자 정보 조회.
 *
 * <p>API: {@code GET https://www.googleapis.com/oauth2/v3/userinfo} with Bearer token.
 * 응답: {@code sub} (providerId), {@code email}, {@code email_verified}, {@code name}, {@code picture}.</p>
 *
 * <p>{@code email_verified=false} 이면 보안상 인증 실패 처리.</p>
 */
@Component
public class GoogleOAuthProvider implements OAuthProvider {

    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final RestClient restClient;

    public GoogleOAuthProvider(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public SocialProvider supports() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo verifyAndFetch(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        GoogleUserResponse res;
        try {
            res = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserResponse.class);
        } catch (RestClientException e) {
            throw new ExternalApiException("google-oauth", e);
        }

        if (res == null || res.sub() == null || res.email() == null || res.name() == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        // 검증된 이메일만 허용 — null/missing/false 모두 거부 (도용 위험)
        if (!Boolean.TRUE.equals(res.emailVerified())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        try {
            return new OAuthUserInfo(
                    SocialProvider.GOOGLE,
                    res.sub(),
                    new Email(res.email()),
                    res.name(),
                    res.picture()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
    }

    record GoogleUserResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            String name,
            String picture
    ) {}
}
