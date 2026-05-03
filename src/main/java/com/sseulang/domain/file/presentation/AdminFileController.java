package com.sseulang.domain.file.presentation;

import com.sseulang.domain.file.application.FileApplicationService;
import com.sseulang.domain.file.application.dto.PresignRequestItem;
import com.sseulang.domain.file.application.dto.PresignResult;
import com.sseulang.domain.file.presentation.dto.PresignedUrlRequest;
import com.sseulang.domain.file.presentation.dto.PresignedUrlResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 전용 S3 presigned URL 발급. SecurityConfig admin chain 으로 ROLE_ADMIN 강제.
 *
 * <p>일반 사용자 endpoint ({@link FileController}) 와 달리 purpose 화이트리스트 X — 관리자 전용
 * NOTICE / BANNER 등 모든 purpose 자유 발급. 일반 사용자가 NOTICE 폴더에 쓰는 누수 차단 목적
 * (게이트 1 의 USER_ALLOWED 정책 유지).</p>
 */
@Tag(name = "AdminFile", description = "관리자 — S3 presigned URL (모든 purpose)")
@RestController
@RequestMapping("/api/v1/admin/files")
public class AdminFileController {

    private final FileApplicationService fileService;

    public AdminFileController(FileApplicationService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "[관리자] presigned URL 발급",
            description = "purpose 자유 (NOTICE/BANNER 포함). owner=adminId. "
                    + "Content-Type=image/*, ≤5MB, 한 번에 ≤10건, 만료 5분.")
    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> issue(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        List<PresignRequestItem> items = request.files().stream()
                .map(f -> new PresignRequestItem(f.contentType(), f.contentLength()))
                .toList();
        // issue() — USER_ALLOWED 화이트리스트 우회. 권한은 admin chain 이 보장.
        List<PresignResult> results = fileService.issue(request.purpose(), adminId, items);
        return ApiResponse.ok(PresignedUrlResponse.from(results));
    }
}
