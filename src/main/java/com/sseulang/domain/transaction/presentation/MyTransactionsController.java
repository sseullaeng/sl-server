package com.sseulang.domain.transaction.presentation;

import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.application.dto.TransactionRole;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.presentation.dto.TransactionResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction", description = "마이페이지 본인 거래 목록")
@RestController
@RequestMapping("/api/v1/users/me/transactions")
public class MyTransactionsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionApplicationService transactionService;

    public MyTransactionsController(TransactionApplicationService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "내 거래 목록",
            description = "본인이 buyer/seller 로 참여한 거래 페이징. "
                    + "role 미지정 = 양쪽, status 미지정 = 전체(취소 포함). 잘못된 role 값은 양쪽으로 fallback. "
                    + "status 는 단일(`status=채팅중`) 또는 CSV(`status=채팅중,예약,인계완료`) 둘 다 허용 — 잘못된 값은 무시. "
                    + "정렬: createdAt DESC.")
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> listMine(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        TransactionRole roleFilter = TransactionRole.parse(role);
        java.util.Set<TransactionStatus> statuses = parseStatuses(status);

        Page<TransactionResponse> result = transactionService
                .findMyTransactions(userId, roleFilter, statuses, pageable)
                .map(TransactionResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    private static java.util.Set<TransactionStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Set.of();
        java.util.LinkedHashSet<TransactionStatus> result = new java.util.LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(TransactionStatus.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 무시 — 무효 상태값
            }
        }
        return result;
    }
}
