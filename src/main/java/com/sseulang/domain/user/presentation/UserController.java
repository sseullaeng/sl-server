package com.sseulang.domain.user.presentation;

import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.presentation.dto.MeResponse;
import com.sseulang.domain.user.presentation.dto.UserUpdateRequest;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
            description = "AT 쿠키 기반 인증된 사용자 정보. 로그인 후 페이지 새로고침 시 store 재초기화에 사용.")
    @GetMapping("/me")
    public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(MeResponse.from(userService.getById(userId)));
    }

    @Operation(summary = "본인 프로필 수정 (partial)",
            description = "profileImage / nickname 둘 다 optional. null 필드는 변경 X. "
                    + "profileImage 빈 문자열은 이미지 제거. 이메일 미인증 상태에서도 호출 가능 (자금/거래 영향 0). "
                    + "이미지 등록 흐름: 1) POST /files/presigned-url (purpose=PROFILE) → 2) S3 PUT → 3) 본 endpoint 에 받은 key 또는 GET URL 전달.")
    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateMe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.ok(MeResponse.from(
                userService.updateProfile(userId, request.profileImage(), request.nickname())
        ));
    }
}
