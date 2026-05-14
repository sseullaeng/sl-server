package com.sseulang.domain.overdue.presentation;

import com.sseulang.domain.overdue.application.OverdueApplicationService;
import com.sseulang.domain.overdue.domain.OverduePhase;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import com.sseulang.domain.overdue.presentation.dto.AdminOverdueResponse;
import com.sseulang.domain.overdue.presentation.dto.OverdueLegalActionRequest;
import com.sseulang.domain.overdue.presentation.dto.OverdueResolveRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminOverdue", description = "관리자 — 연체 관리")
@RestController
@RequestMapping("/api/v1/admin/overdue")
public class AdminOverdueController {

    private final OverdueApplicationService overdueService;

    public AdminOverdueController(OverdueApplicationService overdueService) {
        this.overdueService = overdueService;
    }

    @Operation(summary = "[관리자] 연체 목록",
            description = "status (진행중/정산완료/법적조치중/종료) + phase (PHASE_1~4) 필터, 페이징.")
    @GetMapping
    public ApiResponse<PageResponse<AdminOverdueResponse>> list(
            @RequestParam(value = "status", required = false) OverdueStatus status,
            @RequestParam(value = "phase", required = false) OverduePhase phase,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                overdueService.adminSearch(status, phase, pageable).map(AdminOverdueResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 연체 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<AdminOverdueResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(AdminOverdueResponse.from(overdueService.adminGet(id)));
    }

    @Operation(summary = "[관리자] 법적 조치 단계 전이",
            description = "내용증명/분쟁조정/소송제기 중 하나로 status=법적조치중, phase=PHASE_4 강제 전이.")
    @PatchMapping("/{id}/legal-action")
    public ApiResponse<Void> markLegalAction(
            @PathVariable("id") Long id,
            @Valid @RequestBody OverdueLegalActionRequest request
    ) {
        overdueService.adminMarkLegalAction(id, request.action());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 강제 종료",
            description = "외부 합의 등으로 운영상 강제 종료. 진행중이면 잔여 보증금 환불, status=종료/정산완료.")
    @PatchMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) OverdueResolveRequest request
    ) {
        overdueService.adminResolve(id, request == null ? null : request.note());
        return ApiResponse.ok();
    }

    @Operation(summary = "[관리자] 강제 재계산 (디버그용)",
            description = "스케줄러 실행 외 임의 시점에서 advanceDay 호출. 연체일 / 차감 / phase 재산정.")
    @PostMapping("/{id}/recompute")
    public ApiResponse<Void> recompute(@PathVariable("id") Long id) {
        overdueService.recompute(id);
        return ApiResponse.ok();
    }
}
