package com.sseulang.domain.block.presentation;

import com.sseulang.domain.block.application.UserBlockApplicationService;
import com.sseulang.domain.block.presentation.dto.UserBlockRequest;
import com.sseulang.domain.block.presentation.dto.UserBlockResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UserBlock", description = "사용자 차단/해제")
@RestController
@RequestMapping("/api/v1/blocks")
public class UserBlockController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserBlockApplicationService blockService;

    public UserBlockController(UserBlockApplicationService blockService) {
        this.blockService = blockService;
    }

    @Operation(summary = "사용자 차단", description = "차단 후 채팅/거래 불가. 멱등 — 이미 차단 상태 재호출 OK.")
    @PostMapping
    public ApiResponse<Void> block(
            @AuthenticationPrincipal Long blockerId,
            @Valid @RequestBody UserBlockRequest request
    ) {
        blockService.block(blockerId, request.userId());
        return ApiResponse.ok();
    }

    @Operation(summary = "사용자 차단 해제", description = "멱등 — 차단 안 한 상태 재호출 OK.")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unblock(
            @AuthenticationPrincipal Long blockerId,
            @PathVariable("userId") Long blockedId
    ) {
        blockService.unblock(blockerId, blockedId);
        return ApiResponse.ok();
    }

    @Operation(summary = "내가 차단한 사용자 목록")
    @GetMapping
    public ApiResponse<PageResponse<UserBlockResponse>> listMine(
            @AuthenticationPrincipal Long blockerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<UserBlockResponse> result = blockService.listMine(blockerId, pageable)
                .map(UserBlockResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
