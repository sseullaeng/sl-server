package com.sseulang.domain.block.presentation;

import com.sseulang.domain.block.application.UserBlockApplicationService;
import com.sseulang.domain.block.presentation.dto.UserBlockRequest;
import com.sseulang.domain.block.presentation.dto.UserBlockResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/blocks")
public class UserBlockController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserBlockApplicationService blockService;

    public UserBlockController(UserBlockApplicationService blockService) {
        this.blockService = blockService;
    }

    @PostMapping
    public ApiResponse<Void> block(
            @AuthenticationPrincipal Long blockerId,
            @Valid @RequestBody UserBlockRequest request
    ) {
        blockService.block(blockerId, request.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unblock(
            @AuthenticationPrincipal Long blockerId,
            @PathVariable("userId") Long blockedId
    ) {
        blockService.unblock(blockerId, blockedId);
        return ApiResponse.ok();
    }

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
