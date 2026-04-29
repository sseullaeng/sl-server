package com.sseulang.domain.file.presentation.dto;

import com.sseulang.domain.file.application.dto.PresignResult;

import java.util.List;

public record PresignedUrlResponse(List<Upload> uploads) {

    public record Upload(String presignedUrl, String key) {
        public static Upload from(PresignResult r) {
            return new Upload(r.presignedUrl(), r.key());
        }
    }

    public static PresignedUrlResponse from(List<PresignResult> results) {
        return new PresignedUrlResponse(results.stream().map(Upload::from).toList());
    }
}
