package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.EmailVerificationService;
import com.sseulang.domain.auth.presentation.dto.VerifyEmailRequest;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "EmailVerification", description = "이메일 인증 메일 발송/확인")
@RestController
@RequestMapping("/api/v1/auth")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    public EmailVerificationController(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Operation(summary = "이메일 인증 토큰 확인 (익명)",
            description = "사용자가 인증 메일 링크 클릭 시 호출. 만료 400 AUTH_VERIFICATION_TOKEN_EXPIRED, 잘못된 토큰 400 AUTH_VERIFICATION_TOKEN_INVALID.")
    @PostMapping("/verify-email")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        verificationService.verifyToken(request.token());
        return ApiResponse.ok();
    }

    @Operation(summary = "인증 메일 재발송",
            description = "로그인 필수. 이미 verified 면 no-op. rate limit 적용 (429 AUTH_VERIFICATION_RESEND_TOO_SOON).")
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resend(@AuthenticationPrincipal Long userId) {
        verificationService.resendForUser(userId);
        return ApiResponse.ok();
    }
}
