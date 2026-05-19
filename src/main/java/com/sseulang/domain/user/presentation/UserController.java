package com.sseulang.domain.user.presentation;

import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.presentation.dto.MeResponse;
import com.sseulang.domain.user.presentation.dto.UserProfileResponse;
import com.sseulang.domain.user.presentation.dto.UserUpdateRequest;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "본인 사용자 정보 조회/수정")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserApplicationService userService;

    public UserController(UserApplicationService userService) {
        this.userService = userService;
    }

    @Operation(summary = "본인 정보 조회",
            description = "AT 쿠키 기반 인증된 사용자 정보. 응답 role 필드로 ADMIN 여부 판별 가능 — 프론트 마이페이지 → 관리 페이지 redirect 분기.")
    @GetMapping("/me")
    public ApiResponse<MeResponse> getMe(
            @AuthenticationPrincipal Long userId,
            Authentication auth
    ) {
        return ApiResponse.ok(MeResponse.from(userService.getById(userId), roleFrom(auth)));
    }

    @Operation(summary = "다른 사용자 공개 프로필 조회",
            description = "ItemDetail 의 sellerId 등으로 호출. 닉네임/프로필이미지/신뢰도/리뷰수/가입일. "
                    + "이메일/잔액/emailVerified 등 민감 정보는 미노출. 인증 불필요 (공개).")
    @GetMapping("/{id}/profile")
    public ApiResponse<UserProfileResponse> getProfile(@PathVariable("id") Long id) {
        return ApiResponse.ok(UserProfileResponse.from(userService.getById(id)));
    }

    @Operation(summary = "본인 프로필 수정 (partial)",
            description = "profileImage / nickname 둘 다 optional. null 필드는 변경 X. "
                    + "profileImage 빈 문자열은 이미지 제거. 이메일 미인증 상태에서도 호출 가능 (자금/거래 영향 0). "
                    + "이미지 등록 흐름: 1) POST /files/presigned-url (purpose=PROFILE) → 2) S3 PUT → 3) 본 endpoint 에 받은 key 또는 GET URL 전달.")
    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateMe(
            @AuthenticationPrincipal Long userId,
            Authentication auth,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.ok(MeResponse.from(
                userService.updateProfile(userId, request.profileImage(), request.nickname()),
                roleFrom(auth)
        ));
    }

    
    private static String roleFrom(Authentication auth) {
        if (auth == null) return "USER";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .findFirst()
                .map(a -> a.substring("ROLE_".length()))
                .orElse("USER");
    }
}
