package com.sseulang.domain.transaction.domain.event;

/**
 * buyer 인수확인 시점 — seller 에게 정산 완료 알림용 (라운드 11). settleAmount 는 seller 에게 적립된 금액.
 */
public record TransactionReceiveConfirmedEvent(
        Long transactionId,
        Long sellerId,
        long settleAmount
) { }
