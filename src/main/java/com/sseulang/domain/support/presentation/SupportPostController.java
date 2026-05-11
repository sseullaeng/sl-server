package com.sseulang.domain.support.presentation;

import com.sseulang.domain.support.application.SupportPostApplicationService;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPostType;
import com.sseulang.domain.support.presentation.dto.SupportPostResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SupportPost", description = "고객지원 FAQ / QNA — 공개 조회")
@RestController
@RequestMapping("/api/v1/support/posts")
public class SupportPostController {

    private final SupportPostApplicationService service;

    public SupportPostController(SupportPostApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "FAQ / QNA 목록", description = "type 필수 (FAQ|QNA). category 선택. 최신순.")
    @GetMapping
    public ApiResponse<PageResponse<SupportPostResponse>> list(
            @RequestParam("type") SupportPostType type,
            @RequestParam(value = "category", required = false) InquiryCategory category,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                service.findVisible(type, category, pageable).map(SupportPostResponse::from)
        ));
    }

    @Operation(summary = "FAQ / QNA 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<SupportPostResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(SupportPostResponse.from(service.findById(id)));
    }
}
