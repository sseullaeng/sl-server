package com.sseulang.domain.transaction.application.dto;

import java.time.LocalDateTime;

/**
 * Review 도메인이 거래 후 양방향 평가를 작성할 때 받는 정보. reviewer 권한·기한 검증은
 * Transaction 도메인이 수행하고, reviewee 는 자동 결정 (참여자 중 reviewer 가 아닌 쪽).
 */
public record ReviewableTransactionResult(
        Long transactionId,
        Long reviewerId,
        Long revieweeId,
        LocalDateTime completedAt
) { }
