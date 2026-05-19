package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.LocalAuthService;
import com.sseulang.domain.auth.application.OAuthLoginService;
import com.sseulang.domain.auth.application.RefreshTokenRotationService;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.presentation.dto.LocalLoginRequest;
import com.sseulang.domain.auth.presentation.dto.LocalSignupRequest;
import com.sseulang.domain.auth.presentation.dto.OAuthLoginRequest;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.presentation.dto.MeResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.CookieUtil;
import com.sseulang.global.security.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Auth", description = "회원가입 / 로그인 (LOCAL·OAuth) / 토큰 / 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RefreshTokenRotationService rotationService;
    private final OAuthLoginService oauthLoginService;
    private final LocalAuthService localAuthService;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;
    private final UserApplicationService userService;

    public AuthController(
            RefreshTokenRotationService rotationService,
            OAuthLoginService oauthLoginService,
            LocalAuthService localAuthService,
            CookieUtil cookieUtil,
            JwtProvider jwtProvider,
            UserApplicationService userService
    ) {
        this.rotationService = rotationService;
        this.oauthLoginService = oauthLoginService;
        this.localAuthService = localAuthService;
        this.cookieUtil = cookieUtil;
        this.jwtProvider = jwtProvider;
        this.userService = userService;
    }

    @Operation(summary = "LOCAL 회원가입",
            description = "이메일/비밀번호 가입. 성공 시 AT(at)/RT(rt) HttpOnly 쿠키 자동 발급 + 인증 메일 발송. "
                    + "이메일 인증 전엔 자금 영향 API 가 403(AUTH_EMAIL_NOT_VERIFIED) 떨굼.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MeResponse>> signup(@Valid @RequestBody LocalSignupRequest request) {
        TokenPair pair = localAuthService.signup(request.toEmailVO(), request.password(), request.nickname());
        return setAuthCookiesWithMe(pair);
    }

    @Operation(summary = "LOCAL 로그인",
            description = "이메일/비밀번호 로그인. 미존재/차단/비밀번호 불일치 모두 동일 응답(401 AUTH_LOGIN_FAILED) — 이메일 존재 여부 leak 방지. "
                    + "성공 시 AT/RT 쿠키 자동 발급.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MeResponse>> login(@Valid @RequestBody LocalLoginRequest request) {
        TokenPair pair = localAuthService.login(request.toEmailVO(), request.password());
        return setAuthCookiesWithMe(pair);
    }

    

    private ResponseEntity<ApiResponse<MeResponse>> setAuthCookiesWithMe(TokenPair pair) {
        ResponseCookie at = cookieUtil.accessTokenCookie(pair.accessToken());
        ResponseCookie rt = cookieUtil.refreshTokenCookie(pair.refreshToken());
        
        var claims = jwtProvider.parse(pair.accessToken());
        MeResponse me = MeResponse.from(userService.getById(claims.userId()), claims.role());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, at.toString())
                .header(HttpHeaders.SET_COOKIE, rt.toString())
                .body(ApiResponse.ok(me));
    }

    @Operation(summary = "OAuth 로그인 (kakao/google) — Authorization Code Grant",
            description = "프론트가 OAuth redirect 로 받은 code + 본인이 쓴 redirectUri 를 본 endpoint 에 POST. "
                    + "백엔드가 provider token endpoint 에 client_id/client_secret 동봉해 access_token 으로 교환 + user info 조회. "
                    + "신규 가입은 email_verified=true 로 즉시 발급 (provider 검증된 이메일). Client Secret 활성화 호환.")
    @PostMapping("/oauth2/{provider}")
    public ResponseEntity<ApiResponse<MeResponse>> oauth2(
            @PathVariable("provider") String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        SocialProvider socialProvider = parseProvider(provider);
        TokenPair pair = oauthLoginService.loginWithCode(socialProvider, request.code(), request.redirectUri());
        return setAuthCookiesWithMe(pair);
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

    @Operation(summary = "AT 재발급 (RT rotation)",
            description = "RT 쿠키로 새 AT/RT 쌍 발급. RT rotation 적용 — 사용된 RT 즉시 폐기. "
                    + "AT 만료(401 AUTH_TOKEN_EXPIRED) 시 axios interceptor 등으로 자동 호출 권장.")
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

    @Operation(summary = "로그아웃",
            description = "AT 즉시 blacklist + RT 폐기 + AT/RT 쿠키 만료. "
                    + "응답 후 모든 인증 요청 401(AUTH_TOKEN_REVOKED) 떨어짐.")
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
