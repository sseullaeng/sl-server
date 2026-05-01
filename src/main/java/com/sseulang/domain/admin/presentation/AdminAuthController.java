package com.sseulang.domain.admin.presentation;

import com.sseulang.domain.admin.application.AdminLoginService;
import com.sseulang.domain.admin.presentation.dto.AdminLoginRequest;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.security.CookieUtil;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 로그인 진입점. SecurityConfig 의 {@code /api/v1/auth/**} permitAll 영역.
 * 일반 사용자 OAuth 흐름 ({@link com.sseulang.domain.auth.presentation.AuthController}) 와 분리.
 */
@Tag(name = "AdminAuth", description = "관리자 로그인")
@RestController
@RequestMapping("/api/v1/auth/admin")
public class AdminAuthController {

    private final AdminLoginService adminLoginService;
    private final CookieUtil cookieUtil;

    public AdminAuthController(AdminLoginService adminLoginService, CookieUtil cookieUtil) {
        this.adminLoginService = adminLoginService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody AdminLoginRequest request) {
        TokenPair pair = adminLoginService.login(request.username(), request.password());

        ResponseCookie at = cookieUtil.accessTokenCookie(pair.accessToken());
        ResponseCookie rt = cookieUtil.refreshTokenCookie(pair.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, at.toString())
                .header(HttpHeaders.SET_COOKIE, rt.toString())
                .body(ApiResponse.ok());
    }
}
