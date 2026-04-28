package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.RefreshTokenRotationService;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.CookieUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RefreshTokenRotationService rotationService;
    private final CookieUtil cookieUtil;

    public AuthController(RefreshTokenRotationService rotationService, CookieUtil cookieUtil) {
        this.rotationService = rotationService;
        this.cookieUtil = cookieUtil;
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
