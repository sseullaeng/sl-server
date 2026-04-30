package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.EmailVerificationService;
import com.sseulang.domain.auth.presentation.dto.VerifyEmailRequest;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 인증 — verify (token 검증, 익명 호출) + resend (재발송, 인증 필수).
 *
 * <p>SecurityConfig 정책:
 * <ul>
 *   <li>{@code /verify-email} — PUBLIC_AUTH_ENDPOINTS, CSRF 면제 (사용자가 메일 링크 클릭하는 흐름)</li>
 *   <li>{@code /resend-verification} — auth 필수 (hasRole("USER")) + CSRF 적용 (게이트 1 round 2 보강)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    public EmailVerificationController(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        verificationService.verifyToken(request.token());
        return ApiResponse.ok();
    }

    /** 본인 인증 메일 재발송 — 로그인 필수. 이미 verified 면 no-op. */
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resend(@AuthenticationPrincipal Long userId) {
        verificationService.resendForUser(userId);
        return ApiResponse.ok();
    }
}
