package com.sseulang.domain.notification.presentation;

import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.presentation.dto.NotificationResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 조회/읽음 처리")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationApplicationService notificationService;

    public NotificationController(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록",
            description = "본인 알림 페이징. read=false 필터는 클라이언트 책임 (현재 서버 필터 X — follow-up).")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> listMine(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<NotificationResponse> result = notificationService.listMine(userId, pageable)
                .map(NotificationResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @Operation(summary = "알림 읽음 처리",
            description = "본인 알림만 (그 외 무시). id 는 MongoDB ObjectId hex 24자.")
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String id
    ) {
        notificationService.markAsRead(id, userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "내 모든 알림 읽음 처리",
            description = "본인 unread 알림 일괄 read (atomic UPDATE multi). 처리 건수 반환.")
    @PatchMapping("/read-all")
    public ApiResponse<MarkAllResponse> markAllAsRead(@AuthenticationPrincipal Long userId) {
        long updated = notificationService.markAllAsRead(userId);
        return ApiResponse.ok(new MarkAllResponse(updated));
    }

    @Operation(summary = "내 unread 알림 개수",
            description = "전체 unread 알림 개수만 단일 count 쿼리로 반환. 첫 페이지 derive 보다 정확 — 21번째 이후도 카운트.")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal Long userId) {
        long count = notificationService.countUnread(userId);
        return ApiResponse.ok(new UnreadCountResponse(count));
    }

    public record MarkAllResponse(long updated) { }

    public record UnreadCountResponse(long unread) { }
}
