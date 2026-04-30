package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.LocalAuthService;
import com.sseulang.domain.auth.application.OAuthLoginService;
import com.sseulang.domain.auth.application.RefreshTokenRotationService;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.presentation.dto.LocalLoginRequest;
import com.sseulang.domain.auth.presentation.dto.LocalSignupRequest;
import com.sseulang.domain.auth.presentation.dto.OAuthLoginRequest;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.CookieUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RefreshTokenRotationService rotationService;
    private final OAuthLoginService oauthLoginService;
    private final LocalAuthService localAuthService;
    private final CookieUtil cookieUtil;

    public AuthController(
            RefreshTokenRotationService rotationService,
            OAuthLoginService oauthLoginService,
            LocalAuthService localAuthService,
            CookieUtil cookieUtil
    ) {
        this.rotationService = rotationService;
        this.oauthLoginService = oauthLoginService;
        this.localAuthService = localAuthService;
        this.cookieUtil = cookieUtil;
    }

    /** LOCAL email/password 가입 — 가입 즉시 자동 로그인 (AT/RT 쿠키 발급). */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody LocalSignupRequest request) {
        TokenPair pair = localAuthService.signup(request.toEmailVO(), request.password(), request.nickname());
        return setAuthCookies(pair);
    }

    /** LOCAL email/password 로그인. 미존재/차단/비밀번호 불일치 모두 동일 응답 (leak 방어). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody LocalLoginRequest request) {
        TokenPair pair = localAuthService.login(request.toEmailVO(), request.password());
        return setAuthCookies(pair);
    }

    private ResponseEntity<ApiResponse<Void>> setAuthCookies(TokenPair pair) {
        ResponseCookie at = cookieUtil.accessTokenCookie(pair.accessToken());
        ResponseCookie rt = cookieUtil.refreshTokenCookie(pair.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, at.toString())
                .header(HttpHeaders.SET_COOKIE, rt.toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/oauth2/{provider}")
    public ResponseEntity<ApiResponse<Void>> oauth2(
            @PathVariable("provider") String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        SocialProvider socialProvider = parseProvider(provider);
        TokenPair pair = oauthLoginService.login(socialProvider, request.accessToken());

        ResponseCookie at = cookieUtil.accessTokenCookie(pair.accessToken());
        ResponseCookie rt = cookieUtil.refreshTokenCookie(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, at.toString())
                .header(HttpHeaders.SET_COOKIE, rt.toString())
                .body(ApiResponse.ok());
    }

    private SocialProvider parseProvider(String pathParam) {
        if (pathParam == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        return switch (pathParam.toLowerCase()) {
            case "kakao" -> SocialProvider.KAKAO;
            case "google" -> SocialProvider.GOOGLE;
            default -> throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        };
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING);
        }
        TokenPair pair = rotationService.rotate(refreshToken);

        ResponseCookie at = cookieUtil.accessTokenCookie(pair.accessToken());
        ResponseCookie rt = cookieUtil.refreshTokenCookie(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, at.toString())
                .header(HttpHeaders.SET_COOKIE, rt.toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = CookieUtil.ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        rotationService.logout(accessToken, refreshToken);

        ResponseCookie atDel = cookieUtil.deleteAccessTokenCookie();
        ResponseCookie rtDel = cookieUtil.deleteRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, atDel.toString())
                .header(HttpHeaders.SET_COOKIE, rtDel.toString())
                .body(ApiResponse.ok());
    }
}
