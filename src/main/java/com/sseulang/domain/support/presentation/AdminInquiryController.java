package com.sseulang.domain.support.presentation;

import com.sseulang.domain.support.application.InquiryApplicationService;
import com.sseulang.domain.support.domain.InquiryStatus;
import com.sseulang.domain.support.presentation.dto.InquiryReplyRequest;
import com.sseulang.domain.support.presentation.dto.InquiryResponse;
import com.sseulang.domain.support.presentation.dto.InquiryStatusRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminInquiry", description = "관리자 — 1:1 문의 답변/관리")
@RestController
@RequestMapping("/api/v1/admin/inquiries")
public class AdminInquiryController {

    private final InquiryApplicationService inquiryService;

    public AdminInquiryController(InquiryApplicationService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @Operation(summary = "[관리자] 1:1 문의 전체 목록", description = "status 필터 선택. 최신순.")
    @GetMapping
    public ApiResponse<PageResponse<InquiryResponse>> list(
            @RequestParam(value = "status", required = false) InquiryStatus status,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                inquiryService.adminFindAll(status, pageable).map(InquiryResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 1:1 문의 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<InquiryResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(InquiryResponse.from(inquiryService.adminFindById(id)));
    }

    @Operation(summary = "[관리자] 답변 작성", description = "status 미지정 시 자동 DONE.")
    @PatchMapping("/{id}/reply")
    public ApiResponse<Void> reply(
            @PathVariable("id") Long id,
            @Valid @RequestBody InquiryReplyRequest request
    ) {
        inquiryService.adminReply(id, request.toCommand());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 상태 변경", description = "보통 PENDING → PROCESSING. 답변 없이 DONE 시도 불가.")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody InquiryStatusRequest request
    ) {
        inquiryService.adminChangeStatus(id, request.status());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 1:1 문의 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        inquiryService.adminDelete(id);
        return ApiResponse.ok();
    }
}
