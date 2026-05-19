package com.sseulang.domain.message.presentation.dto;

import com.sseulang.domain.message.application.dto.MessageSendCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "채팅 메시지 전송 — content 또는 imageUrls 중 최소 1개 필수.")
public record MessageSendRequest(
        @Schema(description = "텍스트 메시지", example = "안녕하세요, 거래 가능한가요?", maxLength = 2000, nullable = true)
        @Size(max = 2000) String content,

        @Schema(description = "첨부 이미지 URL 목록 (S3 업로드 후 키)",
                example = "[\"https://cdn.sseulang.test/messages/abc.jpg\"]", nullable = true)
        List<String> imageUrls
) {
    public MessageSendCommand toCommand(Long chatRoomId, Long senderId) {
        return new MessageSendCommand(chatRoomId, senderId, content, imageUrls);
    }
}
