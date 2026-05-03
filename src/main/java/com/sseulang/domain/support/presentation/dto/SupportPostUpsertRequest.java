package com.sseulang.domain.support.presentation.dto;

import com.sseulang.domain.support.application.dto.SupportPostUpsertCommand;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "FAQ/QNA 게시글 등록·수정 (관리자 전용).")
public record SupportPostUpsertRequest(
        @Schema(allowableValues = {"FAQ", "QNA"})
        @NotNull SupportPostType postType,

        @Schema(allowableValues = {"계정", "거래", "결제", "배송", "기타"})
        @NotNull InquiryCategory category,

        @Schema(example = "결제 후 영수증은 어디서 받을 수 있나요?", maxLength = 500)
        @NotBlank @Size(max = 500) String question,

        @Schema(example = "마이페이지 > 결제 내역에서 다운로드 가능합니다.")
        @NotBlank String answer,

        @Schema(description = "첨부 이미지 URL 배열 (선택)", nullable = true)
        @Size(max = 5) List<@Size(max = 500) String> imageUrls
) {
    public SupportPostUpsertCommand toCommand() {
        return new SupportPostUpsertCommand(postType, category, question, answer, imageUrls);
    }
}
