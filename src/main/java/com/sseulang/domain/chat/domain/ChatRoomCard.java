package com.sseulang.domain.chat.domain;

import com.sseulang.domain.item.domain.TradeType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "chat_room_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomCard {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("chat_room_id")
    private Long chatRoomId;

    @Field("trade_mode")
    private TradeType tradeMode;

    @Field("item_id")
    private Long itemId;

    @Field("item_title")
    private String itemTitle;

    @Field("thumbnail_url")
    private String thumbnailUrl;

    @Field("price")
    private Long price;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    public static ChatRoomCard create(Long chatRoomId, TradeType tradeMode,
                                      Long itemId, String itemTitle,
                                      String thumbnailUrl, Long price) {
        if (chatRoomId == null) throw new IllegalArgumentException("chatRoomId 는 필수입니다");
        if (tradeMode == null) throw new IllegalArgumentException("tradeMode 는 필수입니다");
        if (itemId == null) throw new IllegalArgumentException("itemId 는 필수입니다");
        if (itemTitle == null || itemTitle.isBlank()) throw new IllegalArgumentException("itemTitle 은 필수입니다");
        ChatRoomCard c = new ChatRoomCard();
        c.chatRoomId = chatRoomId;
        c.tradeMode = tradeMode;
        c.itemId = itemId;
        c.itemTitle = itemTitle;
        c.thumbnailUrl = thumbnailUrl;
        c.price = price;
        return c;
    }
}
