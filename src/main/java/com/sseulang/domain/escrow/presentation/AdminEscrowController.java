package com.sseulang.domain.escrow.presentation;

import com.sseulang.domain.escrow.application.EscrowApplicationService;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationResult;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminEscrow", description = "관리자 — 거래대행 신청 모니터링")
@RestController
@RequestMapping("/api/v1/admin/escrow/applications")
public class AdminEscrowController {

    private final EscrowApplicationService service;

    public AdminEscrowController(EscrowApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "거래대행 신청 전체 (admin)", description = "최신순. status 필터는 후속.")
    @GetMapping
    public ApiResponse<PageResponse<EscrowApplicationResult>> listAll(Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.adminListAll(pageable)));
    }

    @Operation(summary = "거래대행 신청 단건 (admin)")
    @GetMapping("/{id}")
    public ApiResponse<EscrowApplicationResult> getOne(@PathVariable Long id) {
        return ApiResponse.ok(service.adminGetById(id));
    }
}
