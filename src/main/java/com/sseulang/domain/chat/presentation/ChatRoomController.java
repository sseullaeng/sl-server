package com.sseulang.domain.chat.presentation;

import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.chat.presentation.dto.ChatRoomCreateRequest;
import com.sseulang.domain.chat.presentation.dto.ChatRoomResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat-rooms")
public class ChatRoomController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomApplicationService chatRoomService;

    public ChatRoomController(ChatRoomApplicationService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    /** 멱등 — 같은 (요청자, 상대, 물품) 채팅방이 있으면 기존 반환, 없으면 생성. */
    @PostMapping
    public ApiResponse<ChatRoomResponse> openFor(
            @AuthenticationPrincipal Long requesterId,
            @Valid @RequestBody ChatRoomCreateRequest request
    ) {
        return ApiResponse.ok(ChatRoomResponse.from(
                chatRoomService.openFor(requesterId, request.itemId())));
    }

    @GetMapping
    public ApiResponse<PageResponse<ChatRoomResponse>> listMine(
            @AuthenticationPrincipal Long requesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<ChatRoomResponse> result = chatRoomService.listMine(requesterId, pageable)
                .map(ChatRoomResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChatRoomResponse> getOne(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(ChatRoomResponse.from(chatRoomService.getOne(id, requesterId)));
    }
}
