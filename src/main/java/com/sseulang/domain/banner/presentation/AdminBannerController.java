package com.sseulang.domain.banner.presentation;

import com.sseulang.domain.banner.application.BannerApplicationService;
import com.sseulang.domain.banner.presentation.dto.BannerActiveRequest;
import com.sseulang.domain.banner.presentation.dto.BannerResponse;
import com.sseulang.domain.banner.presentation.dto.BannerUpsertRequest;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminBanner", description = "관리자 — 배너 관리")
@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController {

    private final BannerApplicationService bannerService;

    public AdminBannerController(BannerApplicationService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "[관리자] 배너 전체 목록", description = "active=false / 윈도우 외 배너도 모두 포함.")
    @GetMapping
    public ApiResponse<PageResponse<BannerResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                bannerService.adminFindAll(pageable).map(BannerResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 배너 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<BannerResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(BannerResponse.from(bannerService.adminFindById(id)));
    }

    @Operation(summary = "[관리자] 배너 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody BannerUpsertRequest request
    ) {
        Long id = bannerService.create(adminId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(id));
    }

    @Operation(summary = "[관리자] 배너 전체 수정")
    @PatchMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody BannerUpsertRequest request
    ) {
        bannerService.update(id, request.toCommand());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 배너 활성/비활성 토글")
    @PatchMapping("/{id}/active")
    public ApiResponse<Void> setActive(
            @PathVariable("id") Long id,
            @Valid @RequestBody BannerActiveRequest request
    ) {
        bannerService.setActive(id, request.active());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 배너 삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        bannerService.delete(id);
        return ApiResponse.ok();
    }
}
