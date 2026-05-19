package com.sseulang.domain.item.presentation;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.AdminItemSearchCriteria;
import com.sseulang.domain.item.application.dto.AdminItemSort;
import com.sseulang.domain.item.application.dto.AdminItemSummaryResult;
import com.sseulang.domain.item.application.dto.AdminItemDetailResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.item.presentation.dto.AdminItemDetailResponse;
import com.sseulang.domain.item.presentation.dto.AdminItemSummaryResponse;
import com.sseulang.domain.report.domain.UserReportRepository;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "AdminItem", description = "관리자 — 물품 관리")
@RestController
@RequestMapping("/api/v1/admin/items")
public class AdminItemController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ItemApplicationService itemService;
    private final UserReportRepository userReportRepository;
    private final TransactionRepository transactionRepository;

    public AdminItemController(
            ItemApplicationService itemService,
            UserReportRepository userReportRepository,
            TransactionRepository transactionRepository
    ) {
        this.itemService = itemService;
        this.userReportRepository = userReportRepository;
        this.transactionRepository = transactionRepository;
    }

    @Operation(summary = "[관리자] 전체 물품 목록",
            description = "q=title or seller nickname, status/tradeType/category 필터, "
                    + "createdAfter/Before 기간, sort=latest|view_desc|report_desc.")
    @GetMapping
    public ApiResponse<PageResponse<AdminItemSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) TradeType tradeType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) LocalDateTime createdAfter,
            @RequestParam(required = false) LocalDateTime createdBefore,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AdminItemSort sortEnum = switch (sort) {
            case "view_desc" -> AdminItemSort.VIEW_DESC;
            case "report_desc" -> AdminItemSort.REPORT_DESC;
            default -> AdminItemSort.LATEST;
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<AdminItemSummaryResult> result = itemService.adminSearch(
                new AdminItemSearchCriteria(q, status, tradeType, categoryId, createdAfter, createdBefore, sortEnum),
                pageable
        );
        return ApiResponse.ok(PageResponse.from(result.map(AdminItemSummaryResponse::from)));
    }

    @Operation(summary = "[관리자] 물품 상세 + 신고 이력 + 거래 이력")
    @GetMapping("/{id}")
    public ApiResponse<AdminItemDetailResponse> getOne(@PathVariable("id") Long id) {
        AdminItemDetailResult result = itemService.adminGetDetail(
                id,
                userReportRepository.findByItemIdOrderByCreatedAtDesc(id),
                transactionRepository.findByItemIdOrderByIdDesc(id)
        );
        return ApiResponse.ok(AdminItemDetailResponse.from(result));
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
