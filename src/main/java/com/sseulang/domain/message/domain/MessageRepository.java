package com.sseulang.domain.message.domain;

import java.util.List;

public interface MessageRepository {

    Message save(Message message);

    

    List<Message> findPage(Long chatRoomId, String beforeId, int size);
}
