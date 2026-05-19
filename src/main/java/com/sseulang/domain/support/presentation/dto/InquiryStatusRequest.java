package com.sseulang.domain.support.presentation.dto;

import com.sseulang.domain.support.domain.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자 status 변경. 답변 없이 PROCESSING 으로 옮길 때 사용.")
public record InquiryStatusRequest(
        @Schema(allowableValues = {"PENDING", "PROCESSING", "DONE"})
        @NotNull InquiryStatus status
) {}
