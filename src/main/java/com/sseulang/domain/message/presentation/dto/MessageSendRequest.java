package com.sseulang.domain.message.presentation.dto;

import com.sseulang.domain.message.application.dto.MessageSendCommand;
import jakarta.validation.constraints.Size;

import java.util.List;

/** content 또는 imageUrls 둘 중 하나는 필수. ApplicationService 가 도메인 invariant 로 검증. */
public record MessageSendRequest(
        @Size(max = 2000) String content,
        List<String> imageUrls
) {
    public MessageSendCommand toCommand(Long chatRoomId, Long senderId) {
        return new MessageSendCommand(chatRoomId, senderId, content, imageUrls);
    }
}
