package com.sseulang.domain.notification.presentation;

import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.presentation.dto.NotificationResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
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

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationApplicationService notificationService;

    public NotificationController(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

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

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String id
    ) {
        notificationService.markAsRead(id, userId);
        return ApiResponse.ok();
    }
}
