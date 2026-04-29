package com.sseulang.domain.message.application.dto;

import java.util.List;

public record MessageSendCommand(
        Long chatRoomId,
        Long senderId,
        String content,
        List<String> imageUrls
) { }
