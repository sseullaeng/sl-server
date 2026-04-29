package com.sseulang.domain.item.presentation;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.presentation.dto.ItemDetailResponse;
import com.sseulang.domain.item.presentation.dto.ItemIdResponse;
import com.sseulang.domain.item.presentation.dto.ItemRegisterRequest;
import com.sseulang.domain.item.presentation.dto.ItemSummaryResponse;
import com.sseulang.domain.item.presentation.dto.ItemUpdateRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ItemApplicationService itemService;

    public ItemController(ItemApplicationService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItemIdResponse>> register(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody ItemRegisterRequest request
    ) {
        Long id = itemService.register(request.toCommand(sellerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ItemIdResponse(id)));
    }

    @GetMapping
    public ApiResponse<PageResponse<ItemSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<ItemSummaryResponse> result = itemService.listLatest(pageable)
                .map(ItemSummaryResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<ItemDetailResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(ItemDetailResponse.from(itemService.getById(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id,
            @Valid @RequestBody ItemUpdateRequest request
    ) {
        itemService.update(id, requesterId, request.toCommand());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        itemService.delete(id, requesterId);
        return ApiResponse.ok();
    }
}
