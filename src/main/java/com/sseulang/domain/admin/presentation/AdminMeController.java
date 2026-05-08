package com.sseulang.domain.admin.presentation;

import com.sseulang.domain.admin.application.AdminApplicationService;
import com.sseulang.domain.admin.presentation.dto.AdminMeResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 본인 정보 조회 — admin chain ROLE_ADMIN 강제 ({@code /api/v1/admin/**}).
 * 일반 사용자의 {@code GET /api/v1/users/me} 는 ROLE_USER 만 허용 (ADMIN 차단) 이라 admin 별도 endpoint.
 */
@Tag(name = "AdminMe", description = "관리자 — 본인 정보")
@RestController
@RequestMapping("/api/v1/admin/me")
public class AdminMeController {

    private final AdminApplicationService adminService;

    public AdminMeController(AdminApplicationService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "[관리자] 본인 정보 조회",
            description = "AT(role=ADMIN) 쿠키 기반 인증된 admin 본인 정보. 두 출처 모두 처리: "
                    + "(1) admins 테이블 (username/password 로그인) → admin.username/name. "
                    + "(2) OAuth allowlist 매치 (app.admin.user-emails) → user.email/nickname. "
                    + "프론트가 admin 페이지 store 초기화에 사용.")
    @GetMapping
    public ApiResponse<AdminMeResponse> getMe(@AuthenticationPrincipal Long subjectId) {
        return ApiResponse.ok(adminService.getMe(subjectId));
    }
}
