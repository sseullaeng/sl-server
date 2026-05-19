package com.sseulang.domain.notice.presentation;

import com.sseulang.domain.notice.application.NoticeApplicationService;
import com.sseulang.domain.notice.domain.NoticeType;
import com.sseulang.domain.notice.presentation.dto.NoticeResponse;
import com.sseulang.domain.notice.presentation.dto.NoticeToggleRequest;
import com.sseulang.domain.notice.presentation.dto.NoticeUpsertRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminNotice", description = "관리자 — 공지 작성/관리")
@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    private final NoticeApplicationService noticeService;

    public AdminNoticeController(NoticeApplicationService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "[관리자] 공지 전체 목록", description = "미공개/윈도우 외 공지 모두 포함.")
    @GetMapping
    public ApiResponse<PageResponse<NoticeResponse>> list(
            @RequestParam(value = "type", required = false) NoticeType type,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                noticeService.adminFindAll(type, pageable).map(NoticeResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 공지 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<NoticeResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(NoticeResponse.from(noticeService.adminFindById(id)));
    }

    @Operation(summary = "[관리자] 공지 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody NoticeUpsertRequest request
    ) {
        Long id = noticeService.create(adminId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(id));
    }

    @Operation(summary = "[관리자] 공지 전체 수정")
    @PatchMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody NoticeUpsertRequest request
    ) {
        noticeService.update(id, request.toCommand());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 공지 상단 고정 토글")
    @PatchMapping("/{id}/pin")
    public ApiResponse<Void> setPinned(
            @PathVariable("id") Long id,
            @Valid @RequestBody NoticeToggleRequest request
    ) {
        noticeService.setPinned(id, request.value());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 공지 게시/비게시 토글")
    @PatchMapping("/{id}/publish")
    public ApiResponse<Void> setPublished(
            @PathVariable("id") Long id,
            @Valid @RequestBody NoticeToggleRequest request
    ) {
        noticeService.setPublished(id, request.value());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 공지 삭제 (hard delete)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        noticeService.delete(id);
        return ApiResponse.ok();
    }
}
