package com.sseulang.domain.item.presentation;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 의 물품 관리. 라운드 12 PR-F #6 — admin 이 본인 아닌 물품도 soft delete 가능.
 *
 * <p>admin chain (ROLE_ADMIN 강제) — user chain 의 {@link ItemController#delete} 와 분리.</p>
 */
@Tag(name = "AdminItem", description = "관리자 — 물품 관리")
@RestController
@RequestMapping("/api/v1/admin/items")
public class AdminItemController {

    private final ItemApplicationService itemService;

    public AdminItemController(ItemApplicationService itemService) {
        this.itemService = itemService;
    }

    @Operation(summary = "[관리자] 물품 강제 삭제 (soft delete)",
            description = "owner 검증 우회. status=삭제 전환. 관련 거래/채팅방은 유지. audit 로그 기록.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long adminId,
            @PathVariable("id") Long id
    ) {
        itemService.adminDelete(id, adminId);
        return ApiResponse.ok();
    }
}
