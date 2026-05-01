package com.sseulang.domain.file.presentation.dto;

import com.sseulang.domain.file.application.dto.PresignResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "S3 presigned URL 발급 응답 — 각 파일에 대해 PUT 가능한 URL + key 반환. 5분 만료.")
public record PresignedUrlResponse(List<Upload> uploads) {

    @Schema(description = "단일 파일 업로드 URL 쌍.")
    public record Upload(
            @Schema(example = "https://bucket.s3.ap-northeast-2.amazonaws.com/items/100/abc.jpg?X-Amz-...",
                    description = "이 URL 로 PUT (Content-Type 헤더 포함)") String presignedUrl,
            @Schema(example = "items/100/abc.jpg", description = "S3 객체 키 — Item 등록 시 imageUrls 에 그대로 사용") String key
    ) {
        public static Upload from(PresignResult r) {
            return new Upload(r.presignedUrl(), r.key());
        }
    }

    public static PresignedUrlResponse from(List<PresignResult> results) {
        return new PresignedUrlResponse(results.stream().map(Upload::from).toList());
    }
}
