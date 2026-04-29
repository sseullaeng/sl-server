package com.sseulang.domain.message.application;

import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.message.application.dto.MessageResult;
import com.sseulang.domain.message.application.dto.MessageSendCommand;
import com.sseulang.domain.message.domain.Message;
import com.sseulang.domain.message.domain.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 채팅 메시지 도메인. 가이드 §4.10 — MongoDB messages 컬렉션 + ChatRoom 메타 갱신 분리 트랜잭션.
 *
 * <p>흐름: ChatRoom 참여자 검증 → MongoDB Message 저장 → MySQL ChatRoom.last_message + unread 원자 갱신.
 * MongoDB / MySQL 트랜잭션이 분리되므로 가이드 §4.10 정합 — 실패 시 보상 X (로그 + 재시도).</p>
 *
 * <p>Notification 자동 생성 + WebSocket broadcast 는 Phase 2 영역.</p>
 */
@Service
@Transactional(readOnly = true)
public class MessageApplicationService {

    private final MessageRepository messageRepository;
    private final ChatRoomApplicationService chatRoomApplicationService;

    public MessageApplicationService(
            MessageRepository messageRepository,
            ChatRoomApplicationService chatRoomApplicationService
    ) {
        this.messageRepository = messageRepository;
        this.chatRoomApplicationService = chatRoomApplicationService;
    }

    /**
     * 메시지 발신. 텍스트 또는 이미지 (XOR — 한쪽 비어있어야).
     */
    public MessageResult send(MessageSendCommand cmd) {
        chatRoomApplicationService.requireParticipant(cmd.chatRoomId(), cmd.senderId());

        Message saved;
        boolean hasImages = cmd.imageUrls() != null && !cmd.imageUrls().isEmpty();
        if (hasImages) {
            saved = messageRepository.save(Message.image(cmd.chatRoomId(), cmd.senderId(), cmd.imageUrls()));
        } else {
            saved = messageRepository.save(Message.text(cmd.chatRoomId(), cmd.senderId(), cmd.content()));
        }

        // ChatRoom 메타 갱신 — 별도 MySQL 트랜잭션. 실패 시 로그 (가이드 §4.10) — 재시도 정책은 후속 작업.
        chatRoomApplicationService.recordIncomingMessage(cmd.chatRoomId(), cmd.senderId(), saved.preview());

        return MessageResult.from(saved);
    }

    public List<MessageResult> listPage(Long chatRoomId, Long requesterId, String beforeId, int size) {
        chatRoomApplicationService.requireParticipant(chatRoomId, requesterId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return messageRepository.findPage(chatRoomId, beforeId, safeSize).stream()
                .map(MessageResult::from)
                .toList();
    }
}
