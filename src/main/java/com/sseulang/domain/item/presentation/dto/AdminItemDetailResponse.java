package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.AdminItemDetailResult;
import com.sseulang.domain.item.application.dto.ItemDetailResult;

import java.time.LocalDateTime;
import java.util.List;

public record AdminItemDetailResponse(
        ItemDetailResult item,
        String sellerNickname,
        long reportCount,
        List<ReportHistoryItem> reportHistory,
        List<TransactionHistoryItem> transactionHistory
) {
    public record ReportHistoryItem(
            Long id, Long reporterId, String reason, String status, LocalDateTime createdAt
    ) {
        public static ReportHistoryItem from(AdminItemDetailResult.ReportHistoryItem r) {
            return new ReportHistoryItem(r.id(), r.reporterId(), r.reason(), r.status(), r.createdAt());
        }
    }

    public record TransactionHistoryItem(
            Long id, Long buyerId, String buyerNickname, String status, long price, LocalDateTime completedAt
    ) {
        public static TransactionHistoryItem from(AdminItemDetailResult.TransactionHistoryItem t) {
            return new TransactionHistoryItem(t.id(), t.buyerId(), t.buyerNickname(), t.status(), t.price(), t.completedAt());
        }
    }

    public static AdminItemDetailResponse from(AdminItemDetailResult r) {
        return new AdminItemDetailResponse(
                r.item(), r.sellerNickname(), r.reportCount(),
                r.reportHistory().stream().map(ReportHistoryItem::from).toList(),
                r.transactionHistory().stream().map(TransactionHistoryItem::from).toList()
        );
    }
}
