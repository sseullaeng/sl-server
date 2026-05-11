package com.sseulang.domain.notice.presentation;

import com.sseulang.domain.notice.application.NoticeApplicationService;
import com.sseulang.domain.notice.domain.NoticeType;
import com.sseulang.domain.notice.presentation.dto.NoticeResponse;
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

@Tag(name = "Notice", description = "공지 조회 (공개)")
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeApplicationService noticeService;

    public NoticeController(NoticeApplicationService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "공지 목록 (공개)",
            description = "노출 윈도우 통과한 published 공지만. type 필터 옵션. pinned 우선 정렬.")
    @GetMapping
    public ApiResponse<PageResponse<NoticeResponse>> list(
            @RequestParam(value = "type", required = false) NoticeType type,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                noticeService.findVisible(type, pageable).map(NoticeResponse::from)
        ));
    }

    @Operation(summary = "공지 단건 조회 (공개)",
            description = "조회 시 viewCount 1 증가. 미공개·윈도우 외 공지는 404 NOTICE_NOT_FOUND.")
    @GetMapping("/{id}")
    public ApiResponse<NoticeResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(NoticeResponse.from(noticeService.viewById(id)));
    }
}
