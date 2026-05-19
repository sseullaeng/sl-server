package com.sseulang.domain.message.presentation.dto;

import com.sseulang.domain.message.application.dto.MessageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "채팅 메시지 — MongoDB 저장. content/imageUrls 둘 중 하나 이상 채워짐.")
public record MessageResponse(
        @Schema(example = "65a1b2c3d4e5f60001234567", description = "MongoDB ObjectId hex") String id,
        @Schema(example = "8") Long chatRoomId,
        @Schema(example = "200") Long senderId,
        @Schema(example = "안녕하세요, 거래 가능할까요?") String content,
        @Schema(description = "이미지 메시지 URL 목록 (텍스트 메시지면 빈 배열)") List<String> imageUrls,
        Instant createdAt
) {
    public static MessageResponse from(MessageResult r) {
        return new MessageResponse(
                r.id(), r.chatRoomId(), r.senderId(),
                r.content(), r.imageUrls(), r.createdAt()
        );
    }
}
