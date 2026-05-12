package com.sseulang.domain.delivery.presentation;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.delivery.application.dto.AdminDeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.delivery.presentation.dto.AdminDeliveryResponse;
import com.sseulang.domain.delivery.presentation.dto.AdminDeliveryStatsResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "AdminDelivery", description = "관리자 — 배달 이력 / 통계")
@RestController
@RequestMapping("/api/v1/admin/deliveries")
public class AdminDeliveryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DeliveryApplicationService service;

    public AdminDeliveryController(DeliveryApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "[관리자] 전체 배달 이력",
            description = "status/riderId/requesterId 필터, createdAfter/Before 기간, sort=latest|picked_up_desc.")
    @GetMapping
    public ApiResponse<PageResponse<AdminDeliveryResponse>> list(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Long riderId,
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) LocalDateTime createdAfter,
            @RequestParam(required = false) LocalDateTime createdBefore,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<AdminDeliveryResult> result = service.adminSearch(
                status, riderId, requesterId, createdAfter, createdBefore, sort, pageable);
        return ApiResponse.ok(PageResponse.from(result.map(AdminDeliveryResponse::from)));
    }

    @Operation(summary = "[관리자] 배달 상태별 카운트",
            description = "byStatus 맵, total 합계, todayNew (오늘 신규 신청 수).")
    @GetMapping("/stats")
    public ApiResponse<AdminDeliveryStatsResponse> stats() {
        return ApiResponse.ok(AdminDeliveryStatsResponse.from(service.adminGetStatsV2()));
    }
}
