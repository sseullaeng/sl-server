package com.sseulang.domain.user.presentation;

import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.presentation.dto.AdminUserResponse;
import com.sseulang.domain.user.presentation.dto.UserBlockRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 회원 관리. SecurityConfig admin chain 으로 ROLE_ADMIN 강제.
 */
@Tag(name = "AdminUser", description = "관리자 — 사용자 목록/차단")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserApplicationService userService;

    public AdminUserController(UserApplicationService userService) {
        this.userService = userService;
    }

    @Operation(summary = "[관리자] 회원 목록", description = "차단/탈퇴 포함 전체.")
    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                userService.adminFindAll(pageable).map(AdminUserResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 회원 단건 조회 (민감 정보 제외)")
    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(AdminUserResponse.from(userService.getById(id)));
    }

    @Operation(summary = "[관리자] 회원 차단/해제 토글", description = "blocked=true 면 로그인 차단 + 토큰 폐기.")
    @PatchMapping("/{id}/block")
    public ApiResponse<Void> setBlocked(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserBlockRequest request
    ) {
        userService.adminSetBlocked(id, request.blocked());
        return ApiResponse.ok();
    }
}
