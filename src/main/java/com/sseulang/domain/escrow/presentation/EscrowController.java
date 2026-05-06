package com.sseulang.domain.escrow.presentation;

import com.sseulang.domain.escrow.application.EscrowApplicationService;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationResult;
import com.sseulang.domain.escrow.application.dto.EscrowLinkResult;
import com.sseulang.domain.escrow.presentation.dto.EscrowApplicationCancelRequest;
import com.sseulang.domain.escrow.presentation.dto.EscrowApplicationCreateRequest;
import com.sseulang.domain.escrow.presentation.dto.EscrowLinkCreateRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Escrow", description = "거래대행 (Escrow) — link 생성/진입 + 폼 제출 + 결제 + 정산.")
@RestController
@RequestMapping("/api/v1/escrow")
public class EscrowController {

    private final EscrowApplicationService service;

    public EscrowController(EscrowApplicationService service) {
        this.service = service;
    }

    // =========================================================
    // Link
    // =========================================================
    @Operation(summary = "거래대행 link 생성 (신청자)",
            description = "이메일 인증 필수. UUID v4 token + 24h 만료 (env override 가능).")
    @PostMapping("/links")
    public ResponseEntity<ApiResponse<EscrowLinkResult>> createLink(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody EscrowLinkCreateRequest request
    ) {
        EscrowLinkResult result = service.createLink(request.toCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    @Operation(summary = "거래대행 link 진입 (수신자)",
            description = "비로그인 OK — 결정 #1 A1. 신청자 닉네임 + role + feePayer + 만료 노출.")
    @GetMapping("/links/{linkToken}")
    public ApiResponse<EscrowLinkResult> getLink(@PathVariable String linkToken) {
        return ApiResponse.ok(service.getByToken(linkToken));
    }

    // =========================================================
    // Application
    // =========================================================
    @Operation(summary = "거래대행 폼 제출 (수신자)",
            description = "이메일 인증 필수. linkToken 매칭 + atomic claim + snapshot 저장. 동일 사용자 재제출 시 idempotent.")
    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<EscrowApplicationResult>> createApplication(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody EscrowApplicationCreateRequest request
    ) {
        EscrowApplicationResult result = service.createApplication(request.toCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    @Operation(summary = "본인 거래대행 신청 목록", description = "최신순. 필터는 후속 (5/11 단순).")
    @GetMapping("/applications/me")
    public ApiResponse<PageResponse<EscrowApplicationResult>> listMine(
            @AuthenticationPrincipal Long userId,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(service.listMine(userId, pageable)));
    }

    @Operation(summary = "거래대행 신청 단건", description = "참여자만 (initiator/receiver).")
    @GetMapping("/applications/{id}")
    public ApiResponse<EscrowApplicationResult> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(service.getById(id, userId));
    }

    @Operation(summary = "거래대행 신청 취소",
            description = "매칭 전엔 양쪽 환불, 매칭 후엔 결정 #6 (귀책자 100% 부담 — 추가 결제 흐름은 R1).")
    @PatchMapping("/applications/{id}/cancel")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EscrowApplicationCancelRequest request
    ) {
        service.cancel(id, userId, request.reason());
        return ApiResponse.ok();
    }

    @Operation(summary = "buyer 수령 확인 (Mode B)",
            description = "Mode B INTERNAL 거래만. 진행중 → 완료 + 정산 (seller 적립, rider 적립).")
    @PostMapping("/applications/{id}/confirm-receipt")
    public ApiResponse<Void> confirmReceipt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        // 라이더 식별은 Delivery 도메인에서 — Service 가 알아서 조회.
        // 5/11 단순화: riderId null 전달 — Service 가 delivery row 조회하지 않고 정산 시 누락 가능.
        // TODO(R1): Service 안에서 deliveryRepository.findByEscrowApplicationId 로 rider 조회.
        service.confirmReceipt(id, userId, null);
        return ApiResponse.ok();
    }
}
