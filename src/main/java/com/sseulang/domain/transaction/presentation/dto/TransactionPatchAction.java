package com.sseulang.domain.transaction.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 상태 전이 액션. 예약(seller) / 인계확인(seller) / 인수확인(buyer) / 취소(양쪽). "
        + "대여 한정 — 반납요청(buyer, 인계완료→반납요청) / 회신확인(seller, 반납요청→거래완료).")
public enum TransactionPatchAction {
    예약,
    인계확인,
    인수확인,
    완료,
    취소,
    반납요청,
    회신확인
}
