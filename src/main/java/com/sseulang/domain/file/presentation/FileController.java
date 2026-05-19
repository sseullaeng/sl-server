package com.sseulang.domain.file.presentation;

import com.sseulang.domain.file.application.FileApplicationService;
import com.sseulang.domain.file.application.dto.PresignRequestItem;
import com.sseulang.domain.file.application.dto.PresignResult;
import com.sseulang.domain.file.presentation.dto.PresignedUrlRequest;
import com.sseulang.domain.file.presentation.dto.PresignedUrlResponse;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "S3 presigned URL 발급",
            description = "이메일 인증 필수. purpose=PROFILE/ITEM 만 (그 외 403 FORBIDDEN). "
                    + "Content-Type=image/*, ≤5MB, 한 번에 ≤10건. URL 5분 만료. 발급 후 클라이언트가 S3 에 PUT 직접 업로드.")
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
