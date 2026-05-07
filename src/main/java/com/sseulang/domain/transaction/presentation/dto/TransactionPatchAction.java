package com.sseulang.domain.transaction.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PATCH /transactions/{id} body 의 action 값. 라운드 11 합의 (B-2).
 * 사용자 의도 표현 — TransactionStatus enum 과 별도로 둔다 (예: 인계확인 → 인계완료 status 전이).
 */
@Schema(description = "거래 상태 전이 액션. 예약(seller) / 인계확인(seller) / 인수확인(buyer) / 취소(양쪽).")
public enum TransactionPatchAction {
    예약,
    인계확인,
    인수확인,
    취소
}
