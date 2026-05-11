package com.sseulang.domain.chat.presentation.dto;

import com.sseulang.domain.chat.application.dto.ChatRoomResult;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "1:1 채팅방. viewer 기준 enrich — opponent* / myUnread / item* 자동 join.")
public record ChatRoomResponse(
        @Schema(example = "8") Long id,
        @Schema(example = "42") Long itemId,

        @Schema(example = "200", description = "viewer 의 상대방 사용자 id") Long opponentId,
        @Schema(example = "쓸랭이", description = "상대방 닉네임") String opponentNickname,
        @Schema(description = "상대방 프로필 이미지 URL (없으면 null)") String opponentProfileImage,
        @Schema(example = "3", description = "viewer 의 미읽음 카운트") int myUnread,
        @Schema(example = "아이폰 14 Pro", description = "아이템 제목") String itemTitle,
        @Schema(description = "아이템 썸네일 URL (없으면 null)") String itemThumbnailUrl,
        @Schema(example = "true", description = "viewer 가 이 채팅방의 아이템 판매자인지") boolean isSeller,
        @Schema(example = "false", description = "본인이 채팅방을 나갔는지 (soft hide). true 면 본인 목록에서 제외됨") boolean iLeft,
        @Schema(example = "false", description = "상대방이 채팅방을 나갔는지. true 면 메시지 send 차단 + 시스템 메시지 노출") boolean opponentLeft,

        @Schema(example = "안녕하세요...", description = "최근 메시지 미리보기") String lastMessage,
        LocalDateTime lastMessageAt,
        @Schema(description = "false 면 차단/예약 등으로 비활성") boolean active,

        @Schema(description = "정규화 raw — user1Id < user2Id. 호환용 유지, 신규 코드는 opponentId 사용 권장") Long user1Id,
        @Schema(description = "정규화 raw — 호환용 유지") Long user2Id,
        @Schema(description = "raw — user1 미읽음. 호환용 유지, 신규 코드는 myUnread 사용 권장") int user1Unread,
        @Schema(description = "raw — user2 미읽음. 호환용 유지") int user2Unread,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        @Schema(description = "채팅방 시스템 카드 — 첫 메시지 도착 시 lazy 생성. 없으면 null.")
        SystemCard card
) {
    @Schema(description = "거래 모드 + 아이템 요약 카드.")
    public record SystemCard(
            @Schema(example = "대여") TradeType tradeMode,
            @Schema(example = "42") Long itemId,
            @Schema(example = "맥북 프로") String itemTitle,
            @Schema(description = "아이템 썸네일 URL") String itemThumbnailUrl,
            @Schema(example = "30000") Long price
    ) {
        public static SystemCard from(ChatRoomResult.SystemCard c) {
            if (c == null) return null;
            return new SystemCard(c.tradeMode(), c.itemId(), c.itemTitle(), c.itemThumbnailUrl(), c.price());
        }
    }

    public static ChatRoomResponse from(ChatRoomResult r) {
        return new ChatRoomResponse(
                r.id(), r.itemId(),
                r.opponentId(), r.opponentNickname(), r.opponentProfileImage(),
                r.myUnread(),
                r.itemTitle(), r.itemThumbnailUrl(), r.isSeller(),
                r.iLeft(), r.opponentLeft(),
                r.lastMessage(), r.lastMessageAt(), r.active(),
                r.user1Id(), r.user2Id(), r.user1Unread(), r.user2Unread(),
                r.createdAt(), r.updatedAt(),
                SystemCard.from(r.card())
        );
    }
}
