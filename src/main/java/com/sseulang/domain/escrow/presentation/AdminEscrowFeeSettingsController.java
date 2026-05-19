package com.sseulang.domain.escrow.presentation;

import com.sseulang.domain.escrow.application.EscrowFeeSettingsApplicationService;
import com.sseulang.domain.escrow.presentation.dto.EscrowFeeSettingsPatchRequest;
import com.sseulang.domain.escrow.presentation.dto.EscrowFeeSettingsResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "AdminEscrowFeeSettings", description = "관리자 — 거래대행 수수료 정책 운영")
@RestController
@RequestMapping("/api/v1/admin/escrow/fee-settings")
public class AdminEscrowFeeSettingsController {

    private final EscrowFeeSettingsApplicationService service;

    public AdminEscrowFeeSettingsController(EscrowFeeSettingsApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "현재 수수료 정책 조회")
    @GetMapping
    public ApiResponse<EscrowFeeSettingsResponse> get() {
        return ApiResponse.ok(EscrowFeeSettingsResponse.from(service.get()));
    }

    @Operation(summary = "수수료 정책 변경 (관리자)",
            description = "변경 즉시 신규 application 부터 적용. 진행 중 N건은 snapshot 보존 (영향 X).")
    @PatchMapping
    public ApiResponse<Map<String, Object>> update(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody EscrowFeeSettingsPatchRequest req
    ) {
        var updated = service.update(
                req.commissionRate(),
                req.fuelPricePerL(), req.baseFuelPrice(),
                req.baseDeliveryFee(), req.baseKmRate(), req.fuelEfficiency(), req.minDeliveryFee(),
                req.truckBaseDeliveryFee(), req.truckBaseKmRate(), req.truckFuelEfficiency(), req.truckMinDeliveryFee(),
                adminId
        );
        long inProgress = service.countInProgress();
        return ApiResponse.ok(Map.of(
                "settings", EscrowFeeSettingsResponse.from(updated),
                "inProgressCount", inProgress,
                "message", "수수료 정책이 변경되었습니다. 진행 중 " + inProgress + "건은 snapshot 으로 보존되어 영향 없습니다."
        ));
    }
}
