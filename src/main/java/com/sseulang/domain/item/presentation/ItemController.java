package com.sseulang.domain.item.presentation;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.item.presentation.dto.ItemDetailResponse;
import com.sseulang.domain.item.presentation.dto.ItemIdResponse;
import com.sseulang.domain.item.presentation.dto.ItemRegisterRequest;
import com.sseulang.domain.item.presentation.dto.ItemSummaryResponse;
import com.sseulang.domain.item.presentation.dto.ItemUpdateRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Item", description = "물품 등록·검색·수정·삭제·예약 상태 조회")
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ItemApplicationService itemService;

    public ItemController(ItemApplicationService itemService) {
        this.itemService = itemService;
    }

    @Operation(summary = "물품 등록",
            description = "이메일 인증 필수. imageUrls 의 임시 폴더(items/{userId}/) 가 등록 후 정식 폴더(items/{itemId}/) 로 자동 promote.")
    @PostMapping
    public ResponseEntity<ApiResponse<ItemIdResponse>> register(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody ItemRegisterRequest request
    ) {
        Long id = itemService.register(request.toCommand(sellerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ItemIdResponse(id)));
    }

    @Operation(summary = "물품 검색·페이징 (공개)",
            description = "FULLTEXT(ngram) 검색. q 1글자는 LIKE 폴백. categoryId/tradeType/minPrice/maxPrice/tag 조합 필터. "
                    + "sort 옵션: latest(default) / price_asc / price_desc / view_desc / wishlist_desc. 인증 불필요.")
    @GetMapping
    public ApiResponse<PageResponse<ItemSummaryResponse>> list(
            @AuthenticationPrincipal(errorOnInvalidType = false) Long viewerId,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "tradeType", required = false) TradeType tradeType,
            @RequestParam(name = "minPrice", required = false) Long minPrice,
            @RequestParam(name = "maxPrice", required = false) Long maxPrice,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        ItemSearchCriteria criteria = new ItemSearchCriteria(
                q, categoryId, tradeType, minPrice, maxPrice, tag,
                com.sseulang.domain.item.application.dto.ItemSort.parse(sort));

        // 비로그인 — viewerId null → isWishlisted 항상 false. 로그인 시 단일 SELECT 로 enrich.
        Page<ItemSummaryResponse> result = itemService.search(criteria, pageable, viewerId)
                .map(ItemSummaryResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @Operation(summary = "물품 상세 조회 (공개)",
            description = "조회 시 viewCount 1 증가. 삭제된 물품은 404. 인증 불필요.")
    @GetMapping("/{id}")
    public ApiResponse<ItemDetailResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(ItemDetailResponse.from(itemService.getById(id)));
    }

    @Operation(summary = "물품 수정",
            description = "본인 물품만. imageUrls non-null 이면 전체 교체 + 임시→정식 promote. hashtags non-null 이면 전체 교체.")
    @PatchMapping("/{id}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id,
            @Valid @RequestBody ItemUpdateRequest request
    ) {
        itemService.update(id, requesterId, request.toCommand());
        return ApiResponse.ok();
    }

    @Operation(summary = "물품 삭제 (soft delete)",
            description = "본인 물품만. status=삭제 로 전환. 관련 거래/채팅방은 유지.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        itemService.delete(id, requesterId);
        return ApiResponse.ok();
    }
}
