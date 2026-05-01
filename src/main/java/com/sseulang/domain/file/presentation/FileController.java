package com.sseulang.domain.file.presentation;

import com.sseulang.domain.file.application.FileApplicationService;
import com.sseulang.domain.file.application.dto.PresignRequestItem;
import com.sseulang.domain.file.application.dto.PresignResult;
import com.sseulang.domain.file.presentation.dto.PresignedUrlRequest;
import com.sseulang.domain.file.presentation.dto.PresignedUrlResponse;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "File", description = "S3 presigned URL 발급")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileApplicationService fileService;

    public FileController(FileApplicationService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> issue(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        List<PresignRequestItem> items = request.files().stream()
                .map(f -> new PresignRequestItem(f.contentType(), f.contentLength()))
                .toList();
        List<PresignResult> results = fileService.issueForUser(request.purpose(), userId, items);
        return ApiResponse.ok(PresignedUrlResponse.from(results));
    }
}
