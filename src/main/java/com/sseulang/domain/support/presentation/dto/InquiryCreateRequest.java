package com.sseulang.domain.support.presentation.dto;

import com.sseulang.domain.support.application.dto.InquiryCreateCommand;
import com.sseulang.domain.support.domain.InquiryCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "1:1 문의 작성. imageUrls 는 사전에 SUPPORT presigned 로 업로드한 S3 URL. 라운드 12 PR-G — 사진 1장 이상 필수 + 내용 최소 10자.")
public record InquiryCreateRequest(
        @Schema(description = "카테고리", example = "결제", allowableValues = {"계정", "거래", "결제", "배송", "기타"})
        @NotNull InquiryCategory category,

        @Schema(example = "환불 처리가 안 됩니다", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(example = "어제 17시쯤 결제했는데 아직 환불이 안 됐습니다.", description = "본문 — 최소 10자")
        @NotBlank @Size(min = 10, max = 5000, message = "내용은 10자 이상 입력해 주세요") String content,

        @Schema(example = "user@example.com", description = "답변 받을 이메일")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "첨부 이미지 URL 배열 (1~5장 필수)")
        @NotEmpty(message = "첨부 사진은 1장 이상 필수입니다")
        @Size(max = 5) List<@Size(max = 500) String> imageUrls
) {
    public InquiryCreateCommand toCommand() {
        return new InquiryCreateCommand(category, title, content, email, imageUrls);
    }
}
