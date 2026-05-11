package com.sseulang.domain.support.presentation;

import com.sseulang.domain.support.application.InquiryApplicationService;
import com.sseulang.domain.support.domain.InquiryStatus;
import com.sseulang.domain.support.presentation.dto.InquiryCreateRequest;
import com.sseulang.domain.support.presentation.dto.InquiryResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inquiry", description = "1:1 문의 — 본인 작성/조회/삭제")
@RestController
@RequestMapping("/api/v1/support/inquiries")
public class InquiryController {

    private final InquiryApplicationService inquiryService;

    public InquiryController(InquiryApplicationService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @Operation(summary = "1:1 문의 작성")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InquiryCreateRequest request
    ) {
        Long id = inquiryService.create(userId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(id));
    }

    @Operation(summary = "본인 1:1 문의 목록", description = "status 필터 선택. 최신순.")
    @GetMapping("/me")
    public ApiResponse<PageResponse<InquiryResponse>> findMine(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "status", required = false) InquiryStatus status,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                inquiryService.findMine(userId, status, pageable).map(InquiryResponse::from)
        ));
    }

    @Operation(summary = "본인 1:1 문의 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<InquiryResponse> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(InquiryResponse.from(inquiryService.findOwnedById(id, userId)));
    }

    @Operation(summary = "본인 1:1 문의 삭제", description = "PENDING 상태일 때만. 답변 시작 후엔 INQUIRY_INVALID_STATE.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        inquiryService.deleteByOwner(id, userId);
        return ApiResponse.ok();
    }
}
