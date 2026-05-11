package com.sseulang.domain.escrow.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 거래대행 결제 응답 (PR-B-5 라운드 12).
 *
 * @param status 호출 후 application 상태. 한쪽만 paid 면 "결제대기" 유지, 양쪽 paid 시 "결제완료" 또는 그 이후 (라이더 매칭 결과에 따라).
 */
@Schema(description = "거래대행 본인 share 결제 응답 — 후속 상태 노출.")
public record EscrowPayResponse(
        @Schema(example = "결제완료", description = "결제대기 | 결제완료 | 진행중") String status
) { }
