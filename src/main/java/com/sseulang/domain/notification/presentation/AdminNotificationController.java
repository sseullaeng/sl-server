package com.sseulang.domain.notification.presentation;

import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 공지 broadcast — 활성 사용자 전원에게 시스템 알림 INSERT.
 *
 * <p>SecurityConfig admin chain 으로 ROLE_ADMIN 강제. targetRole 은 현재 ALL 만 — ADMIN/USER 분기는 후속.</p>
 */
@Tag(name = "AdminNotification", description = "관리자 — 알림 broadcast")
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final NotificationApplicationService notificationService;

    public AdminNotificationController(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "[관리자] 전체 알림 broadcast (멱등성 — round 12)",
            description = "활성 사용자(blocked/deleted 제외) 전원에게 동일 알림 발송. type=공지 고정. "
                    + "round 12 — idempotencyKey 제공 시 같은 키 재호출에서 중복 INSERT 차단. "
                    + "미제공 시 서버 UUID 자동 생성 → broadcastId 응답으로 반환. 중간 실패 시 "
                    + "같은 broadcastId 로 재호출하면 이미 INSERT 된 건은 skip.")
    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<BroadcastResponse>> broadcast(@Valid @RequestBody BroadcastRequest request) {
        var result = notificationService.broadcast(request.title(), request.content(), request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(new BroadcastResponse(result.broadcastId(), result.sent())));
    }

    @Schema(description = "Broadcast 요청. targetRole 은 현재 사용 안 함 (ALL 고정).")
    public record BroadcastRequest(
            @Schema(example = "5월 점검 안내", maxLength = 200)
            @NotBlank @Size(max = 200) String title,

            @Schema(example = "5월 6일 03:00~05:00 결제 일시 중단됩니다.")
            @NotBlank String content,

            @Schema(description = "현재 무시 (ALL 고정). 향후 USER/ADMIN 등 분기 예정.", nullable = true)
            String targetRole,

            @Schema(description = "멱등키 (round 12). 같은 키로 재호출 시 중복 알림 차단. " +
                    "미제공 시 서버 UUID 자동 생성. 중간 실패 후 같은 키로 재호출하면 부분 복구.",
                    example = "broadcast-2026-05-05-001", nullable = true)
            String idempotencyKey
    ) { }

    @Schema(description = "Broadcast 응답 — broadcastId (재호출 멱등키) + 실제 INSERT 건수.")
    public record BroadcastResponse(
            @Schema(example = "broadcast-2026-05-05-001") String broadcastId,
            @Schema(example = "1234") long sent
    ) { }
}
