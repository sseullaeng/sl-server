package com.sseulang.domain.item.application.dto;

import java.time.LocalDateTime;
import java.util.List;

// 라운드 12 — admin item 상세. ItemDetail + 신고 이력 + 거래 이력.
public record AdminItemDetailResult(
        ItemDetailResult item,
        String sellerNickname,
        long reportCount,
        List<ReportHistoryItem> reportHistory,
        List<TransactionHistoryItem> transactionHistory
) {
    public record ReportHistoryItem(
            Long id,
            Long reporterId,
            String reason,
            String status,            // 접수/처리중/처리완료/반려
            LocalDateTime createdAt
    ) { }

    public record TransactionHistoryItem(
            Long id,
            Long buyerId,
            String buyerNickname,
            String status,            // 채팅중/예약/인계완료/거래완료/취소
            long price,
            LocalDateTime completedAt
    ) { }
}
