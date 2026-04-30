package com.sseulang.domain.file.presentation.dto;

import com.sseulang.domain.file.domain.FilePurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "S3 presigned URL 발급 요청. 클라이언트가 직접 S3 업로드 후, 반환된 URL 을 Item/Message 에 사용.")
public record PresignedUrlRequest(
        @Schema(description = "업로드 용도", example = "ITEM_IMAGE",
                allowableValues = {"ITEM_IMAGE", "PROFILE_IMAGE", "MESSAGE_IMAGE"})
        @NotNull FilePurpose purpose,

        @Schema(description = "업로드 파일 메타 목록")
        @NotEmpty @Valid List<PresignedFileItem> files
) {
    @Schema(description = "단일 파일 메타")
    public record PresignedFileItem(
            @Schema(description = "MIME 타입", example = "image/jpeg")
            @NotNull String contentType,

            @Schema(description = "바이트 길이", example = "524288")
            @NotNull Long contentLength
    ) { }
}
