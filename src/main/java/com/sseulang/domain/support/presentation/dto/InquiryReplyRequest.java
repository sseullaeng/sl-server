package com.sseulang.domain.support.presentation.dto;

import com.sseulang.domain.support.application.dto.InquiryReplyCommand;
import com.sseulang.domain.support.domain.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 답변 작성. status 생략 시 자동 DONE.")
public record InquiryReplyRequest(
        @Schema(example = "환불은 영업일 기준 3~5일 이내 완료됩니다.")
        @NotBlank String adminReply,

        @Schema(description = "지정 시 그 상태로 변경. 미지정 시 DONE.", nullable = true,
                allowableValues = {"PROCESSING", "DONE"})
        InquiryStatus status
) {
    public InquiryReplyCommand toCommand() {
        return new InquiryReplyCommand(adminReply, status);
    }
}
