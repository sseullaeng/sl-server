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
public class GoogleOAuthProvider implements OAuthProvider {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthProvider(
            RestClient.Builder builder,
            @Value("${app.oauth2.google.client-id:}") String clientId,
            @Value("${app.oauth2.google.client-secret:}") String clientSecret
    ) {
        this.restClient = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public SocialProvider supports() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo exchangeCodeAndFetch(String code, String redirectUri) {
        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        String accessToken = exchangeCodeForToken(code, redirectUri);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCodeForToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

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

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("id_token") String idToken,
            String scope
    ) {}

    record GoogleUserResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            String name,
            String picture
    ) {}
}
