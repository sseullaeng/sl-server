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
 * 카카오 access_token 검증 + 사용자 정보 조회.
 *
 * <p>API: {@code GET https://kapi.kakao.com/v2/user/me} with Bearer token.
 * 응답에서 {@code id} (providerId), {@code kakao_account.email}, {@code kakao_account.profile.nickname/profile_image_url}
 * 추출.</p>
 *
 * <p>모든 실패(토큰 무효 / 네트워크 / 응답 파싱 / 동의 항목 누락)는 {@code AUTH_OAUTH_FAILED} 로 통일.</p>
 */
@Component
public class KakaoOAuthProvider implements OAuthProvider {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoOAuthProvider(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public SocialProvider supports() {
        return SocialProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo verifyAndFetch(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        KakaoUserResponse res;
        try {
            res = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException e) {
            // 외부 인프라 예외 — anti-corruption wrap. 호출자가 정책에 맞춰 변환.
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
            // 동의 항목 누락 — 사용자가 약관에서 거부했거나 개발자 콘솔 권한 미설정
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        // 게이트 1 round 2 — 카카오 응답에 미인증 이메일이 포함될 수 있어 takeover/auto-verified 의
        // 전제("provider 가 검증한 이메일") 가 깨질 수 있다. is_email_valid + is_email_verified 둘 다
        // true 인 이메일만 허용 — 둘 중 하나라도 false/null 이면 OAuth 거부.
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
            // 잘못된 이메일 형식 등 — provider 가 비정상 응답
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
    }

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
