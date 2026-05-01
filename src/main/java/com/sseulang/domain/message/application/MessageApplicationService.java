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

/**
 * 채팅 메시지 도메인. 가이드 §4.10:
 *
 * <ol>
 *   <li>채팅방 참여자 검증 + 상대방 식별</li>
 *   <li>MongoDB Message 저장</li>
 *   <li>MySQL ChatRoom.last_message + unread 원자 갱신 (가이드 §4.10 트랜잭션 분리, 실패 시 로그)</li>
 *   <li>상대방 Notification 생성 + STOMP push</li>
 *   <li>채팅방 토픽 broadcast</li>
 * </ol>
 *
 * <p>각 단계 실패는 다음 단계 진행을 막지 않는다 — 메시지는 잃어도 비즈니스 크리티컬 X.
 * 본 PR(Phase 2) 의 핵심 — WebSocket+STOMP 통합은 가이드 §9.6 즉시 게이트 1 영역.</p>
 */
@Service
@Transactional(readOnly = true)
public class MessageApplicationService {

    private final MessageRepository messageRepository;
    private final ChatRoomApplicationService chatRoomApplicationService;
    private final NotificationApplicationService notificationApplicationService;
    private final RealtimePublisher realtimePublisher;  // 단위 테스트 호환용 (deprecated 직접 호출)
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

    /**
     * 메시지 발신. 텍스트 또는 이미지 (XOR — 한쪽 비어있어야).
     *
     * <p>명시 {@code @Transactional} (write) — 클래스 레벨 readOnly 무력화. ChatRoom 메타 갱신은 본 트랜잭션 안.
     * MongoDB 저장은 트랜잭션 밖, broadcast/notify 는 동기 (가이드 §4.10 — 실패 시 보상 X).</p>
     *
     * <p>TODO: broadcast/notify 를 {@code TransactionSynchronization.AFTER_COMMIT} 으로 미루는 정합성 보강은
     * Codex 게이트 1 검증의 후속 권고 영역.</p>
     */
    @Transactional
    public MessageResult send(MessageSendCommand cmd) {
        Long opponentId = chatRoomApplicationService.findOpponent(cmd.chatRoomId(), cmd.senderId());

        Message saved;
        boolean hasImages = cmd.imageUrls() != null && !cmd.imageUrls().isEmpty();
        if (hasImages) {
            saved = messageRepository.save(Message.image(cmd.chatRoomId(), cmd.senderId(), cmd.imageUrls()));
        } else {
            saved = messageRepository.save(Message.text(cmd.chatRoomId(), cmd.senderId(), cmd.content()));
        }

        // ChatRoom 메타 갱신 — 별도 MySQL 트랜잭션. 실패 시 로그 (가이드 §4.10).
        chatRoomApplicationService.recordIncomingMessage(cmd.chatRoomId(), cmd.senderId(), saved.preview());

        MessageResult result = MessageResult.from(saved);

        // 상대방 알림 생성 (트랜잭션 안에서 저장)
        Notification notification = notificationApplicationService.notify(
                opponentId,
                NotificationType.메시지,
                "새 메시지",
                saved.preview(),
                "chat-room",
                cmd.chatRoomId()
        );

        // 실시간 publish 는 AFTER_COMMIT 으로 분리 (follow-up #19) — 트랜잭션 롤백 시 발송 X.
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
