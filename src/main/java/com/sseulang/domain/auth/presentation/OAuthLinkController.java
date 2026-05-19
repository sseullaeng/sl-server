package com.sseulang.domain.auth.presentation;

import com.sseulang.domain.auth.application.OAuthAccountLinkService;
import com.sseulang.domain.auth.presentation.dto.OAuthLinkConfirmRequest;
import com.sseulang.domain.auth.presentation.dto.OAuthLinkPreviewResponse;
import com.sseulang.domain.auth.presentation.dto.OAuthLoginRequest;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.presentation.dto.MeResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth-Link", description = "LOCAL 계정에 OAuth 명시 연결 (preview → confirm)")
@RestController
@RequestMapping("/api/v1/auth/social-link")
@PreAuthorize("isAuthenticated()")
public class OAuthLinkController {

    private static final String DEFAULT_ROLE = "USER";

    private final OAuthAccountLinkService linkService;
    private final UserApplicationService userService;

    public OAuthLinkController(OAuthAccountLinkService linkService, UserApplicationService userService) {
        this.linkService = linkService;
        this.userService = userService;
    }

    @Operation(summary = "OAuth 연결 사전 조회",
            description = "로그인된 LOCAL 사용자가 OAuth code 를 보내면 provider 측 이메일을 검증하고 5분짜리 linkKey 를 발급. "
                    + "키만 받고 실제 연결은 confirm 단계에서.")
    @PostMapping("/{provider}/preview")
    public ResponseEntity<ApiResponse<OAuthLinkPreviewResponse>> preview(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable("provider") String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        SocialProvider sp = parseProvider(provider);
        OAuthAccountLinkService.LinkPreviewResult r = linkService.preview(
                currentUserId, sp, request.code(), request.redirectUri()
        );
        return ResponseEntity.ok(ApiResponse.ok(new OAuthLinkPreviewResponse(
                r.linkKey(), r.provider(), r.providerEmail(), r.expiresInSeconds()
        )));
    }

    @Operation(summary = "OAuth 연결 확정",
            description = "preview 에서 받은 linkKey 로 LOCAL 계정에 소셜을 추가 연결. LOCAL 비밀번호는 유지 — 양쪽 로그인 모두 가능.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<MeResponse>> confirm(
            @AuthenticationPrincipal Long currentUserId,
            @Valid @RequestBody OAuthLinkConfirmRequest request
    ) {
        User user = linkService.confirm(currentUserId, request.linkKey());
        return ResponseEntity.ok(ApiResponse.ok(MeResponse.from(user, DEFAULT_ROLE)));
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
}
