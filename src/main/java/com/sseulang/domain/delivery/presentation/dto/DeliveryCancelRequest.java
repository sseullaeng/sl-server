package com.sseulang.domain.delivery.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "배달 요청 취소. 모집중 상태만 취소 가능 (수락 이후는 분쟁 처리 영역).")
public record DeliveryCancelRequest(
        @Schema(description = "취소 사유 (선택)", example = "직접 가기로 변경", maxLength = 255, nullable = true)
        @Size(max = 255) String reason
) {
}
