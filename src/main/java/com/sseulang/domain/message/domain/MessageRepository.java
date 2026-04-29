package com.sseulang.domain.message.domain;

import java.util.List;

public interface MessageRepository {

    Message save(Message message);

    /**
     * 특정 채팅방의 메시지를 커서 페이징으로 조회. 가이드 §6.3 — {@code ?before={messageId}&size=30}.
     * before 가 null 이면 가장 최근부터. 결과는 최신순 (id desc).
     */
    List<Message> findPage(Long chatRoomId, String beforeId, int size);
}
