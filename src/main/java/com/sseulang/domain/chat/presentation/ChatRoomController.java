package com.sseulang.domain.chat.presentation;

import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.chat.presentation.dto.ChatRoomCreateRequest;
import com.sseulang.domain.chat.presentation.dto.ChatRoomResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ChatRoom", description = "1:1 채팅방 개설/조회")
@RestController
@RequestMapping("/api/v1/chat-rooms")
public class ChatRoomController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomApplicationService chatRoomService;

    public ChatRoomController(ChatRoomApplicationService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @Operation(summary = "채팅방 개설 (멱등)",
            description = "이메일 인증 필수. 같은 (요청자, 상대, 물품) 채팅방이 있으면 기존 반환, 없으면 생성. "
                    + "본인 물품 거부(403 CHAT_FORBIDDEN). 삭제/비공개 물품 거부.")
    @PostMapping
    public ApiResponse<ChatRoomResponse> openFor(
            @AuthenticationPrincipal Long requesterId,
            @Valid @RequestBody ChatRoomCreateRequest request
    ) {
        return ApiResponse.ok(ChatRoomResponse.from(
                chatRoomService.openFor(requesterId, request.itemId(), request.tradeMode())));
    }

    @Operation(summary = "내 채팅방 목록",
            description = "본인이 참여한 채팅방 페이징. lastMessageAt 최신순.")
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

    @Operation(summary = "채팅방 단건 조회",
            description = "참여자만. 그 외 403 CHAT_FORBIDDEN.")
    @GetMapping("/{id}")
    public ApiResponse<ChatRoomResponse> getOne(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(ChatRoomResponse.from(chatRoomService.getOne(id, requesterId)));
    }

    @Operation(summary = "채팅방 읽음 처리",
            description = "본인 unread 카운트를 0 으로 atomic UPDATE. 상대방 unread 는 영향 X. "
                    + "참여자만 호출 (그 외 403 CHAT_FORBIDDEN). 응답에 myUnread=0 반영된 ChatRoomResponse.")
    @PatchMapping("/{id}/read")
    public ApiResponse<ChatRoomResponse> markAsRead(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(ChatRoomResponse.from(chatRoomService.markAsRead(id, requesterId)));
    }

    @Operation(summary = "채팅방 나가기 (soft hide)",
            description = "본인 측에서 채팅방 hide. 본인 listMine 에서 제외, 상대방은 opponentLeft=true 응답 받음 + 메시지 send 차단. "
                    + "데이터·메시지는 보존 (audit 용). 참여자만 호출 (그 외 403 CHAT_FORBIDDEN). idempotent.")
    @PatchMapping("/{id}/leave")
    public ApiResponse<Void> leave(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        chatRoomService.leave(id, requesterId);
        return ApiResponse.ok();
    }
}
