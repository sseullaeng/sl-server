package com.sseulang.domain.auth.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoOAuthProvider implements OAuthProvider {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public KakaoOAuthProvider(
            RestClient.Builder builder,
            @Value("${app.oauth2.kakao.client-id:}") String clientId,
            @Value("${app.oauth2.kakao.client-secret:}") String clientSecret
    ) {
        this.restClient = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public SocialProvider supports() {
        return SocialProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo exchangeCodeAndFetch(String code, String redirectUri) {
        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        if (clientId == null || clientId.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        String accessToken = exchangeCodeForToken(code, redirectUri);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCodeForToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        TokenResponse res;
        try {
            res = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        if (res == null || res.accessToken() == null || res.accessToken().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        return res.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        KakaoUserResponse res;
        try {
            res = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException e) {
            throw new ExternalApiException("kakao-oauth", e);
        }

        if (res == null || res.id() == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        KakaoAccount account = res.kakaoAccount();
        String email = account != null ? account.email() : null;
        KakaoProfile profile = account != null ? account.profile() : null;
        String nickname = profile != null ? profile.nickname() : null;
        String profileImage = profile != null ? profile.profileImageUrl() : null;

        if (email == null || nickname == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        
        if (!Boolean.TRUE.equals(account.isEmailValid()) || !Boolean.TRUE.equals(account.isEmailVerified())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        try {
            return new OAuthUserInfo(
                    SocialProvider.KAKAO,
                    String.valueOf(res.id()),
                    new Email(email),
                    nickname,
                    profileImage
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            String scope
    ) {}

    record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {}

    record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean isEmailValid,
            @JsonProperty("is_email_verified") Boolean isEmailVerified,
            KakaoProfile profile
    ) {}

    record KakaoProfile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {}
}
