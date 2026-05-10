package com.sseulang.domain.transaction.application.dto;

import java.time.LocalDateTime;

/**
 * 거래 생성 Command. 라운드 12 (#3.2 + 판매자만 정책) — 채팅방 안에서 판매자가 거래 시작.
 *
 * @param requesterId 호출자(판매자) userId. 정책상 item.sellerId 와 일치해야 하며, 그렇지 않으면
 *                    ApplicationService 가 TX_SELLER_ONLY 를 던진다. buyerId 는 chatRoom 의
 *                    상대방에서 도출 (판매자가 거래 시작 시점에 명시 X).
 * @param chatRoomId  채팅방 ID. null 이면 TX_CHATROOM_REQUIRED.
 */
public record TransactionCreateCommand(
        Long itemId,
        Long requesterId,
        Long chatRoomId,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd
) { }
