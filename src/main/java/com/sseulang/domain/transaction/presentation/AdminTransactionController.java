package com.sseulang.domain.transaction.presentation;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.presentation.dto.TransactionResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 관리자 거래 검색·관리. SecurityConfig admin chain 으로 ROLE_ADMIN 강제.
 *
 * <p>round 9 — AdminTransactionListPanel 의 mock 제거용. created_at 기준 [start, end] 범위 +
 * tradeType / status / keyword 필터. keyword 는 itemId/transactionId 숫자 매칭 (email/nickname
 * LIKE 는 follow-up). 최신순.</p>
 */
@Tag(name = "AdminTransaction", description = "관리자 — 거래 검색/관리")
@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

    private final TransactionApplicationService transactionService;

    public AdminTransactionController(TransactionApplicationService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "[관리자] 거래 검색·페이징",
            description = "startDate/endDate (created_at 범위, ISO LocalDateTime). tradeType/status/keyword 모두 선택. "
                    + "keyword 는 transactionId 또는 itemId 숫자 매칭 (email/nickname LIKE 는 follow-up). 최신순.")
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "type", required = false) TradeType tradeType,
            @RequestParam(value = "status", required = false) TransactionStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                transactionService.adminSearch(startDate, endDate, tradeType, status, keyword, pageable)
                        .map(TransactionResponse::from)
        ));
    }
}
