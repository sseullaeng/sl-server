package com.sseulang.domain.user.presentation;

import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.application.dto.AdminUserSearchCriteria;
import com.sseulang.domain.user.domain.UserStatus;
import com.sseulang.domain.user.presentation.dto.AdminUserResponse;
import com.sseulang.domain.user.presentation.dto.UserBlockRequest;
import com.sseulang.domain.user.presentation.dto.UserSuspendRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 관리자 회원 관리. SecurityConfig admin chain 으로 ROLE_ADMIN 강제.
 *
 * <p>v8b: keyword/status/createdAfter/createdBefore 검색·필터, suspend/unsuspend, enriched response.</p>
 */
@Tag(name = "AdminUser", description = "관리자 — 사용자 목록/검색/차단/정지")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserApplicationService userService;

    public AdminUserController(UserApplicationService userService) {
        this.userService = userService;
    }

    @Operation(summary = "[관리자] 회원 목록·검색",
            description = "keyword(닉네임/이메일 LIKE), status(ACTIVE/SUSPENDED/WITHDRAWN/DORMANT), "
                    + "createdAfter/createdBefore (ISO LocalDateTime). 모두 선택. tradeCount/reportCount enriched.")
    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(value = "createdAfter", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAfter,
            @RequestParam(value = "createdBefore", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdBefore,
            Pageable pageable
    ) {
        AdminUserSearchCriteria criteria = new AdminUserSearchCriteria(keyword, status, createdAfter, createdBefore);
        return ApiResponse.ok(PageResponse.from(
                userService.adminSearch(criteria, pageable).map(AdminUserResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 회원 단건 조회 (enriched)")
    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(AdminUserResponse.from(userService.adminGetEnriched(id)));
    }

    @Operation(summary = "[관리자] 회원 차단/해제 토글", description = "blocked=true 면 영구 차단.")
    @PatchMapping("/{id}/block")
    public ApiResponse<Void> setBlocked(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserBlockRequest request
    ) {
        userService.adminSetBlocked(id, request.blocked());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 시한부 활동정지", description = "N일 동안 정지. 만료 후 status 자동 ACTIVE 복귀.")
    @PatchMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserSuspendRequest request
    ) {
        userService.adminSuspend(id, request.days());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 활동정지 즉시 해제")
    @DeleteMapping("/{id}/suspend")
    public ApiResponse<Void> unsuspend(@PathVariable("id") Long id) {
        userService.adminUnsuspend(id);
        return ApiResponse.ok();
    }
}
