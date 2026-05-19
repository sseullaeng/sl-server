package com.sseulang.domain.message.application;

import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.message.application.dto.MessageResult;
import com.sseulang.domain.message.application.dto.MessageSendCommand;
import com.sseulang.domain.message.application.event.ChatRealtimePublishRequestedEvent;
import com.sseulang.domain.message.domain.Message;
import com.sseulang.domain.message.domain.MessageRepository;
import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.domain.notification.application.dto.NotificationResult;
import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.global.websocket.RealtimePublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MessageApplicationService {

    private final MessageRepository messageRepository;
    private final ChatRoomApplicationService chatRoomApplicationService;
    private final NotificationApplicationService notificationApplicationService;
    private final RealtimePublisher realtimePublisher;  
    private final ApplicationEventPublisher eventPublisher;

    public MessageApplicationService(
            MessageRepository messageRepository,
            ChatRoomApplicationService chatRoomApplicationService,
            NotificationApplicationService notificationApplicationService,
            RealtimePublisher realtimePublisher,
            ApplicationEventPublisher eventPublisher
    ) {
        this.messageRepository = messageRepository;
        this.chatRoomApplicationService = chatRoomApplicationService;
        this.notificationApplicationService = notificationApplicationService;
        this.realtimePublisher = realtimePublisher;
        this.eventPublisher = eventPublisher;
    }

    

    @Transactional
    public MessageResult send(MessageSendCommand cmd) {
        
        chatRoomApplicationService.requireSendable(cmd.chatRoomId(), cmd.senderId());
        Long opponentId = chatRoomApplicationService.findOpponent(cmd.chatRoomId(), cmd.senderId());

        
        chatRoomApplicationService.ensureSystemCard(cmd.chatRoomId());

        Message saved;
        boolean hasImages = cmd.imageUrls() != null && !cmd.imageUrls().isEmpty();
        if (hasImages) {
            saved = messageRepository.save(Message.image(cmd.chatRoomId(), cmd.senderId(), cmd.imageUrls()));
        } else {
            saved = messageRepository.save(Message.text(cmd.chatRoomId(), cmd.senderId(), cmd.content()));
        }

        
        chatRoomApplicationService.recordIncomingMessage(cmd.chatRoomId(), cmd.senderId(), saved.preview());

        MessageResult result = MessageResult.from(saved);

        
        Notification notification = notificationApplicationService.notify(
                opponentId,
                NotificationType.메시지,
                "새 메시지",
                saved.preview(),
                "chat-room",
                cmd.chatRoomId()
        );

        
        eventPublisher.publishEvent(new ChatRealtimePublishRequestedEvent(
                cmd.chatRoomId(),
                opponentId,
                result,
                NotificationResult.from(notification)
        ));

        return result;
    }

    public List<MessageResult> listPage(Long chatRoomId, Long requesterId, String beforeId, int size) {
        chatRoomApplicationService.requireParticipant(chatRoomId, requesterId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return messageRepository.findPage(chatRoomId, beforeId, safeSize).stream()
                .map(MessageResult::from)
                .toList();
    }
}
