package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.application.dto.ChatRoomResult;
import com.sseulang.domain.chat.domain.ChatRoom;
import com.sseulang.domain.chat.domain.ChatRoomRepository;
import com.sseulang.domain.chat.domain.ItemView;
import com.sseulang.domain.chat.domain.UserView;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ChatRoomApplicationService {

    /** V1 스키마 {@code uk_chat_rooms_item_users} — 동시 openFor race 시 멱등 처리 (Codex 게이트 2 패턴). */
    private static final String UNIQUE_ITEM_USERS = "uk_chat_rooms_item_users";

    private final ChatRoomRepository chatRoomRepository;
    private final ItemApplicationService itemApplicationService;
    private final com.sseulang.domain.user.application.UserApplicationService userApplicationService;
    private final UserView userView;
    private final ItemView itemView;

    public ChatRoomApplicationService(
            ChatRoomRepository chatRoomRepository,
            ItemApplicationService itemApplicationService,
            com.sseulang.domain.user.application.UserApplicationService userApplicationService,
            UserView userView,
            ItemView itemView
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.itemApplicationService = itemApplicationService;
        this.userApplicationService = userApplicationService;
        this.userView = userView;
        this.itemView = itemView;
    }

    /**
     * 물품 ID 로 채팅방 생성 (가이드 §6.1). 멱등 — 이미 있으면 기존 반환.
     * 본인 물품 거부 (자기 자신과 채팅 X). UNIQUE race 는 좁은 catch 후 재조회.
     * 미인증 사용자의 채팅 스팸 방지 — verified 가드 (게이트 1 round 2).
     */
    @Transactional
    public ChatRoomResult openFor(Long requesterId, Long itemId) {
        userApplicationService.requireVerified(requesterId);
        Long sellerId = itemApplicationService.findSellerOfActiveItem(itemId);
        if (sellerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        ChatRoom room = chatRoomRepository.findByItemAndUsers(itemId, requesterId, sellerId)
                .orElseGet(() -> createWithRaceGuard(itemId, requesterId, sellerId));
        return enrichOne(room, requesterId);
    }

    /**
     * 본인 채팅방 페이징 — 페이지 결과의 opponent userId / itemId 들을 모아 단일 SELECT IN 으로 batch
     * fetch (N+1 회피). viewer 기준 derive 후 응답.
     */
    public Page<ChatRoomResult> listMine(Long userId, Pageable pageable) {
        Page<ChatRoom> page = chatRoomRepository.findMine(userId, pageable);
        if (page.isEmpty()) {
            return page.map(c -> ChatRoomResult.from(c, userId, null, null, null, null, null));
        }
        Set<Long> opponentIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        for (ChatRoom c : page.getContent()) {
            opponentIds.add(userId.equals(c.getUser1Id()) ? c.getUser2Id() : c.getUser1Id());
            itemIds.add(c.getItemId());
        }
        Map<Long, UserView.UserProjection> userMap = userView.findByIds(opponentIds);
        Map<Long, ItemView.ItemProjection> itemMap = itemView.findByIds(itemIds);
        return page.map(c -> enrichWithMaps(c, userId, userMap, itemMap));
    }

    public ChatRoomResult getOne(Long id, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return enrichOne(room, requesterId);
    }

    /**
     * 본인 unread 0 으로 리셋. 본인이 참여자가 아니면 atomic UPDATE 가 영향 0 — 그 경우 사전 권한
     * 검증 후 CHAT_FORBIDDEN. 응답으로 갱신된 채팅방 반환 (myUnread = 0).
     */
    @Transactional
    public ChatRoomResult markAsRead(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        chatRoomRepository.markAsRead(roomId, userId);
        // entity 메모리 상태도 동기화 (응답에 fresh 값 반영)
        room.markAsRead(userId);
        return enrichOne(room, userId);
    }

    /** Message 도메인이 메시지 보낼 권한 검증 시 호출. 참여자 아니면 CHAT_FORBIDDEN. */
    public void requireParticipant(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
    }

    /**
     * 메시지 송신 가능 여부 검증 — 참여자 + 본인 left X + 상대방 left X.
     * 본인 left → CHAT_FORBIDDEN (이미 나간 방).
     * 상대방 left → CHAT_ROOM_OPPONENT_LEFT (상대방이 나가서 차단).
     */
    public void requireSendable(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        if (room.iLeft(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        if (room.opponentLeft(userId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_OPPONENT_LEFT);
        }
    }

    /**
     * 채팅방 나가기 (soft hide) — 본인 측 left_at = NOW(). 데이터/메시지 보존.
     * 본인 listMine 에서 제외, 상대방은 opponentLeft=true 응답 받음.
     * 비참여자 호출은 CHAT_FORBIDDEN. 이미 left 상태도 idempotent (재호출 OK).
     */
    @Transactional
    public void leave(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        chatRoomRepository.markAsLeft(roomId, userId);
    }

    /**
     * 1:1 채팅방의 상대방 userId 반환. requireParticipant 검증을 동시에 수행.
     * 메시지 broadcast 시 상대방 알림 push 용.
     */
    public Long findOpponent(Long chatRoomId, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return requesterId.equals(room.getUser1Id()) ? room.getUser2Id() : room.getUser1Id();
    }

    /**
     * 메시지 발신 시 ChatRoom 메타 갱신 — last_message / last_message_at / 상대방 unread 카운트.
     * 단일 atomic UPDATE. 가이드 §4.10 — MongoDB↔MySQL 트랜잭션 분리 (실패 시 보상 X).
     */
    @Transactional
    public void recordIncomingMessage(Long chatRoomId, Long senderId, String preview) {
        chatRoomRepository.recordIncomingMessage(chatRoomId, senderId, preview);
    }

    /** 단건 enrich — opponent + item batch fetch 후 ChatRoomResult.from. */
    private ChatRoomResult enrichOne(ChatRoom room, Long viewerId) {
        Long opponentId = viewerId.equals(room.getUser1Id()) ? room.getUser2Id() : room.getUser1Id();
        Map<Long, UserView.UserProjection> userMap = userView.findByIds(List.of(opponentId));
        Map<Long, ItemView.ItemProjection> itemMap = itemView.findByIds(List.of(room.getItemId()));
        return enrichWithMaps(room, viewerId, userMap, itemMap);
    }

    private static ChatRoomResult enrichWithMaps(
            ChatRoom c,
            Long viewerId,
            Map<Long, UserView.UserProjection> userMap,
            Map<Long, ItemView.ItemProjection> itemMap
    ) {
        Long opponentId = viewerId.equals(c.getUser1Id()) ? c.getUser2Id() : c.getUser1Id();
        UserView.UserProjection u = userMap.get(opponentId);
        ItemView.ItemProjection i = itemMap.get(c.getItemId());
        return ChatRoomResult.from(
                c, viewerId,
                u != null ? u.nickname() : null,
                u != null ? u.profileImage() : null,
                i != null ? i.title() : null,
                i != null ? i.thumbnailUrl() : null,
                i != null ? i.sellerId() : null
        );
    }

    private ChatRoom createWithRaceGuard(Long itemId, Long requesterId, Long sellerId) {
        ChatRoom newRoom = ChatRoom.openFor(itemId, requesterId, sellerId);
        try {
            return chatRoomRepository.save(newRoom);
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueConflict(violation)) {
                return chatRoomRepository.findByItemAndUsers(itemId, requesterId, sellerId)
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
