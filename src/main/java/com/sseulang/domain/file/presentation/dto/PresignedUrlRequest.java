package com.sseulang.domain.file.presentation.dto;

import com.sseulang.domain.file.domain.FilePurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PresignedUrlRequest(
        @NotNull FilePurpose purpose,
        @NotEmpty @Valid List<PresignedFileItem> files
) {
    public record PresignedFileItem(
            @NotNull String contentType,
            @NotNull Long contentLength
    ) { }
}
