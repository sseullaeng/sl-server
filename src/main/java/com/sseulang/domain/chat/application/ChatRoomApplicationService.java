package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.application.dto.ChatRoomResult;
import com.sseulang.domain.chat.domain.ChatRoom;
import com.sseulang.domain.chat.domain.ChatRoomRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChatRoomApplicationService {

    /** V1 스키마 {@code uk_chat_rooms_item_users} — 동시 openFor race 시 멱등 처리 (Codex 게이트 2 패턴). */
    private static final String UNIQUE_ITEM_USERS = "uk_chat_rooms_item_users";

    private final ChatRoomRepository chatRoomRepository;
    private final ItemApplicationService itemApplicationService;

    public ChatRoomApplicationService(
            ChatRoomRepository chatRoomRepository,
            ItemApplicationService itemApplicationService
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.itemApplicationService = itemApplicationService;
    }

    /**
     * 물품 ID 로 채팅방 생성 (가이드 §6.1). 멱등 — 이미 있으면 기존 반환.
     * 본인 물품 거부 (자기 자신과 채팅 X). UNIQUE race 는 좁은 catch 후 재조회.
     */
    @Transactional
    public ChatRoomResult openFor(Long requesterId, Long itemId) {
        Long sellerId = itemApplicationService.findSellerOfActiveItem(itemId);
        if (sellerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return chatRoomRepository.findByItemAndUsers(itemId, requesterId, sellerId)
                .map(ChatRoomResult::from)
                .orElseGet(() -> createWithRaceGuard(itemId, requesterId, sellerId));
    }

    public Page<ChatRoomResult> listMine(Long userId, Pageable pageable) {
        return chatRoomRepository.findMine(userId, pageable).map(ChatRoomResult::from);
    }

    public ChatRoomResult getOne(Long id, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return ChatRoomResult.from(room);
    }

    private ChatRoomResult createWithRaceGuard(Long itemId, Long requesterId, Long sellerId) {
        ChatRoom newRoom = ChatRoom.openFor(itemId, requesterId, sellerId);
        try {
            return ChatRoomResult.from(chatRoomRepository.save(newRoom));
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueConflict(violation)) {
                return chatRoomRepository.findByItemAndUsers(itemId, requesterId, sellerId)
                        .map(ChatRoomResult::from)
                        .orElseThrow(() -> violation);
            }
            throw violation;
        }
    }

    private static boolean isUniqueConflict(DataIntegrityViolationException violation) {
        Throwable cause = violation;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve
                    && UNIQUE_ITEM_USERS.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                return false;
            }
            cause = next;
        }
        return false;
    }
}
