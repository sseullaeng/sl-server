package com.sseulang.domain.banner.presentation;

import com.sseulang.domain.banner.application.BannerApplicationService;
import com.sseulang.domain.banner.presentation.dto.BannerResponse;
import com.sseulang.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 사용자 활성 배너 목록 — sort_order 정렬. */
@RestController
@RequestMapping("/api/v1/banners")
public class BannerController {

    private final BannerApplicationService bannerService;

    public BannerController(BannerApplicationService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ApiResponse<List<BannerResponse>> list() {
        return ApiResponse.ok(bannerService.findVisible().stream()
                .map(BannerResponse::from)
                .toList());
    }
}
