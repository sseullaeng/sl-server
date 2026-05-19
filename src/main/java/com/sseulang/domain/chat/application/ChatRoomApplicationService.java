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

    
    private static final String UNIQUE_ITEM_USERS_MODE = "uk_chat_rooms_item_users_mode";
    private static final String LEGACY_UNIQUE_ITEM_USERS = "uk_chat_rooms_item_users";

    private final ChatRoomRepository chatRoomRepository;
    private final com.sseulang.domain.chat.domain.ChatRoomCardRepository chatRoomCardRepository;
    private final ItemApplicationService itemApplicationService;
    private final com.sseulang.domain.user.application.UserApplicationService userApplicationService;
    private final UserView userView;
    private final ItemView itemView;
    private final com.sseulang.domain.chat.domain.TransactionView transactionView;
    private final com.sseulang.domain.chat.domain.EscrowApplicationView escrowApplicationView;

    public ChatRoomApplicationService(
            ChatRoomRepository chatRoomRepository,
            com.sseulang.domain.chat.domain.ChatRoomCardRepository chatRoomCardRepository,
            ItemApplicationService itemApplicationService,
            com.sseulang.domain.user.application.UserApplicationService userApplicationService,
            UserView userView,
            ItemView itemView,
            com.sseulang.domain.chat.domain.TransactionView transactionView,
            com.sseulang.domain.chat.domain.EscrowApplicationView escrowApplicationView
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomCardRepository = chatRoomCardRepository;
        this.itemApplicationService = itemApplicationService;
        this.userApplicationService = userApplicationService;
        this.userView = userView;
        this.itemView = itemView;
        this.transactionView = transactionView;
        this.escrowApplicationView = escrowApplicationView;
    }

    

    @Transactional
    public ChatRoomResult openFor(Long requesterId, Long itemId,
                                  com.sseulang.domain.item.domain.TradeType tradeMode) {
        userApplicationService.requireVerified(requesterId);
        Long sellerId = itemApplicationService.findSellerOfActiveItem(itemId);
        if (sellerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        
        
        com.sseulang.domain.item.domain.TradeType mode = tradeMode != null
                ? tradeMode
                : pickPrimary(itemApplicationService.findActiveForTransaction(itemId).tradeTypes());
        ChatRoom room = chatRoomRepository.findByItemAndUsers(itemId, requesterId, sellerId, mode)
                .map(this::reopen)
                .orElseGet(() -> createWithRaceGuard(itemId, requesterId, sellerId, mode));
        return enrichOne(room, requesterId);
    }

    

    public Page<ChatRoomResult> listMine(Long userId, Pageable pageable) {
        Page<ChatRoom> page = chatRoomRepository.findMine(userId, pageable);
        if (page.isEmpty()) {
            return page.map(c -> ChatRoomResult.from(c, userId, null, null, null, null, null, null));
        }
        Set<Long> opponentIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        Set<Long> roomIds = new HashSet<>();
        for (ChatRoom c : page.getContent()) {
            opponentIds.add(userId.equals(c.getUser1Id()) ? c.getUser2Id() : c.getUser1Id());
            itemIds.add(c.getItemId());
            roomIds.add(c.getId());
        }
        Map<Long, UserView.UserProjection> userMap = userView.findByIds(opponentIds);
        Map<Long, ItemView.ItemProjection> itemMap = itemView.findByIds(itemIds);
        Map<Long, ChatRoomResult.SystemCard> cardMap = loadCards(roomIds);
        return page.map(c -> enrichWithMaps(c, userId, userMap, itemMap, cardMap));
    }

    public ChatRoomResult getOne(Long id, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return enrichOne(room, requesterId);
    }

    

    @Transactional
    public void ensureSystemCard(Long chatRoomId) {
        if (chatRoomCardRepository.findByChatRoomId(chatRoomId).isPresent()) {
            return;
        }
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        var itemMap = itemView.findByIds(List.of(room.getItemId()));
        var item = itemMap.get(room.getItemId());
        if (item == null) {
            return;  
        }
        com.sseulang.domain.chat.domain.ChatRoomCard card = com.sseulang.domain.chat.domain.ChatRoomCard.create(
                chatRoomId, room.getTradeMode(),
                item.id(), item.title(), item.thumbnailUrl(), item.price()
        );
        try {
            chatRoomCardRepository.save(card);
        } catch (org.springframework.dao.DuplicateKeyException race) {
            
        }
    }

    

    @Transactional
    public ChatRoomResult markAsRead(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        chatRoomRepository.markAsRead(roomId, userId);
        
        room.markAsRead(userId);
        return enrichOne(room, userId);
    }

    

    public ChatRoomMeta findMetaForParticipant(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return new ChatRoomMeta(room.getId(), room.getItemId(),
                room.getTradeMode(),
                room.iLeft(userId), room.opponentLeft(userId));
    }

    @Transactional
    public ChatRoomMeta reopenForParticipant(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        room.reopen();
        return new ChatRoomMeta(room.getId(), room.getItemId(),
                room.getTradeMode(),
                room.iLeft(userId), room.opponentLeft(userId));
    }

    

    public record ChatRoomMeta(Long chatRoomId, Long itemId,
                               com.sseulang.domain.item.domain.TradeType tradeMode,
                               boolean iLeft, boolean opponentLeft) { }

    
    public void requireParticipant(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
    }

    

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

    

    @Transactional
    public void leave(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        chatRoomRepository.markAsLeft(roomId, userId);
    }

    

    public Long findOpponent(Long chatRoomId, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return requesterId.equals(room.getUser1Id()) ? room.getUser2Id() : room.getUser1Id();
    }

    

    @Transactional
    public void recordIncomingMessage(Long chatRoomId, Long senderId, String preview) {
        chatRoomRepository.recordIncomingMessage(chatRoomId, senderId, preview);
    }

    
    private ChatRoomResult enrichOne(ChatRoom room, Long viewerId) {
        Long opponentId = viewerId.equals(room.getUser1Id()) ? room.getUser2Id() : room.getUser1Id();
        Map<Long, UserView.UserProjection> userMap = userView.findByIds(List.of(opponentId));
        Map<Long, ItemView.ItemProjection> itemMap = itemView.findByIds(List.of(room.getItemId()));
        Map<Long, ChatRoomResult.SystemCard> cardMap = loadCards(List.of(room.getId()));
        return enrichWithMaps(room, viewerId, userMap, itemMap, cardMap);
    }

    private Map<Long, ChatRoomResult.SystemCard> loadCards(java.util.Collection<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return Map.of();
        Map<Long, ChatRoomResult.SystemCard> map = new java.util.HashMap<>();
        for (var c : chatRoomCardRepository.findByChatRoomIdIn(roomIds)) {
            map.put(c.getChatRoomId(), ChatRoomResult.SystemCard.from(c));
        }
        // 활성 거래 / 거래대행 동적 룩업해서 카드에 합성. 거래대행 우선 (INTERNAL 거래대행이면 직거래 Tx 없음).
        var escrowMap = escrowApplicationView.findActiveByChatRoomIds(roomIds);
        var txMap = transactionView.findActiveByChatRoomIds(roomIds);
        for (Long roomId : roomIds) {
            ChatRoomResult.SystemCard base = map.get(roomId);
            if (base == null) continue;
            var e = escrowMap.get(roomId);
            if (e != null) {
                map.put(roomId, base.withEscrow(
                        e.escrowApplicationId(), e.status(), e.deliveryId(), e.transactionId(),
                        e.rentalStartAt(), e.rentalEndAt()));
                continue;
            }
            var t = txMap.get(roomId);
            if (t != null) {
                map.put(roomId, base.withTransaction(
                        t.transactionId(), t.status(),
                        t.rentalStart(), t.rentalEnd()));
            }
        }
        return map;
    }

    private static ChatRoomResult enrichWithMaps(
            ChatRoom c,
            Long viewerId,
            Map<Long, UserView.UserProjection> userMap,
            Map<Long, ItemView.ItemProjection> itemMap,
            Map<Long, ChatRoomResult.SystemCard> cardMap
    ) {
        Long opponentId = viewerId.equals(c.getUser1Id()) ? c.getUser2Id() : c.getUser1Id();
        UserView.UserProjection u = userMap.get(opponentId);
        ItemView.ItemProjection i = itemMap.get(c.getItemId());
        ChatRoomResult.SystemCard card = cardMap != null ? cardMap.get(c.getId()) : null;
        return ChatRoomResult.from(
                c, viewerId,
                u != null ? u.nickname() : null,
                u != null ? u.profileImage() : null,
                i != null ? i.title() : null,
                i != null ? i.thumbnailUrl() : null,
                i != null ? i.sellerId() : null,
                card
        );
    }

    private ChatRoom createWithRaceGuard(Long itemId, Long requesterId, Long sellerId,
                                         com.sseulang.domain.item.domain.TradeType tradeMode) {
        ChatRoom newRoom = ChatRoom.openFor(itemId, requesterId, sellerId, tradeMode);
        try {
            return chatRoomRepository.save(newRoom);
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueConflict(violation)) {
                return chatRoomRepository.findByItemAndUsers(itemId, requesterId, sellerId, tradeMode)
                        .map(this::reopen)
                        .orElseThrow(() -> violation);
            }
            throw violation;
        }
    }

    private ChatRoom reopen(ChatRoom room) {
        room.reopen();
        return room;
    }

    
    private static com.sseulang.domain.item.domain.TradeType pickPrimary(
            java.util.Set<com.sseulang.domain.item.domain.TradeType> types
    ) {
        if (types == null || types.isEmpty()) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        if (types.contains(com.sseulang.domain.item.domain.TradeType.판매)) {
            return com.sseulang.domain.item.domain.TradeType.판매;
        }
        if (types.contains(com.sseulang.domain.item.domain.TradeType.대여)) {
            return com.sseulang.domain.item.domain.TradeType.대여;
        }
        return com.sseulang.domain.item.domain.TradeType.나눔;
    }

    private static boolean isUniqueConflict(DataIntegrityViolationException violation) {
        Throwable cause = violation;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve
                    && isChatRoomUniqueConstraint(cve.getConstraintName())) {
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

    private static boolean isChatRoomUniqueConstraint(String constraintName) {
        return UNIQUE_ITEM_USERS_MODE.equalsIgnoreCase(constraintName)
                || LEGACY_UNIQUE_ITEM_USERS.equalsIgnoreCase(constraintName);
    }
}
