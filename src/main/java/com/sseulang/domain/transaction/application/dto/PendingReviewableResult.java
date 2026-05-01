package com.sseulang.domain.transaction.application.dto;

import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;

/**
 * Review 작성 대기 중 거래 — 거래완료된 본인 참여 거래 중 본인이 아직 review 작성 안 한 것.
 *
 * <p>follow-up #56. 7일 작성 가능 기간 안에 들어오는 거래만. 상대방 id(revieweeId) 와
 * 작성 deadline(completedAt + 7d) 을 함께 노출 — 클라이언트가 "남은 시간" UI 만들기 쉽게.</p>
 */
public record PendingReviewableResult(
        Long transactionId,
        Long itemId,
        Long revieweeId,
        TradeType tradeType,
        long price,
        LocalDateTime completedAt,
        LocalDateTime deadline
) {}
