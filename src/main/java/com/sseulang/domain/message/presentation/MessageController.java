package com.sseulang.domain.message.presentation;

import com.sseulang.domain.message.application.MessageApplicationService;
import com.sseulang.domain.message.presentation.dto.MessageResponse;
import com.sseulang.domain.message.presentation.dto.MessageSendRequest;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Message", description = "채팅 메시지 송수신 + 페이징")
@RestController
@RequestMapping("/api/v1/chat-rooms/{roomId}/messages")
public class MessageController {

    private final MessageApplicationService messageService;

    public MessageController(MessageApplicationService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @AuthenticationPrincipal Long senderId,
            @PathVariable("roomId") Long roomId,
            @Valid @RequestBody MessageSendRequest request
    ) {
        MessageResponse response = MessageResponse.from(
                messageService.send(request.toCommand(roomId, senderId))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /** 커서 페이징 — {@code ?before={messageId}&size=30}. 결과는 최신순. */
    @GetMapping
    public ApiResponse<List<MessageResponse>> listPage(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("roomId") Long roomId,
            @RequestParam(name = "before", required = false) String before,
            @RequestParam(name = "size", defaultValue = "30") int size
    ) {
        List<MessageResponse> page = messageService.listPage(roomId, requesterId, before, size).stream()
                .map(MessageResponse::from)
                .toList();
        return ApiResponse.ok(page);
    }
}
