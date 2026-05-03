package com.sseulang.domain.support.presentation;

import com.sseulang.domain.support.application.SupportPostApplicationService;
import com.sseulang.domain.support.presentation.dto.SupportPostUpsertRequest;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 — FAQ / QNA 게시글 CRUD. SecurityConfig admin chain 으로 ROLE_ADMIN 강제.
 */
@Tag(name = "AdminSupportPost", description = "관리자 — FAQ/QNA 작성")
@RestController
@RequestMapping("/api/v1/admin/support/posts")
public class AdminSupportPostController {

    private final SupportPostApplicationService service;

    public AdminSupportPostController(SupportPostApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "[관리자] FAQ/QNA 게시글 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody SupportPostUpsertRequest request
    ) {
        Long id = service.create(adminId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(id));
    }

    @Operation(summary = "[관리자] FAQ/QNA 게시글 전체 수정")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody SupportPostUpsertRequest request
    ) {
        service.update(id, request.toCommand());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] FAQ/QNA 게시글 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
