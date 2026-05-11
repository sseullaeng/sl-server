package com.sseulang.domain.message.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Document(collection = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    private static final int CONTENT_MAX_LENGTH = 2000;
    private static final int IMAGES_MAX = 5;

    @Id
    private String id;

    @Field("chatRoomId")
    @Indexed
    private Long chatRoomId;

    @Field("senderId")
    private Long senderId;

    @Field("content")
    private String content;

    @Field("imageUrls")
    private List<String> imageUrls;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    public static Message text(Long chatRoomId, Long senderId, String content) {
        validateIds(chatRoomId, senderId);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 는 비어있을 수 없습니다");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("content 는 " + CONTENT_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
        Message m = new Message();
        m.chatRoomId = chatRoomId;
        m.senderId = senderId;
        m.content = content;
        m.imageUrls = List.of();
        return m;
    }

    public static Message image(Long chatRoomId, Long senderId, List<String> imageUrls) {
        validateIds(chatRoomId, senderId);
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("imageUrls 는 비어있을 수 없습니다");
        }
        if (imageUrls.size() > IMAGES_MAX) {
            throw new IllegalArgumentException("imageUrls 는 최대 " + IMAGES_MAX + "장까지 가능합니다");
        }
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("imageUrl 은 비어있을 수 없습니다");
            }
        }
        Message m = new Message();
        m.chatRoomId = chatRoomId;
        m.senderId = senderId;
        m.imageUrls = List.copyOf(imageUrls);
        return m;
    }

    public List<String> getImageUrls() {
        return imageUrls == null ? List.of() : Collections.unmodifiableList(imageUrls);
    }

    public boolean isImageMessage() {
        return imageUrls != null && !imageUrls.isEmpty();
    }

    
    public String preview() {
        if (isImageMessage()) {
            return imageUrls.size() > 1 ? "[사진 " + imageUrls.size() + "장]" : "[사진]";
        }
        return content;
    }

    private static void validateIds(Long chatRoomId, Long senderId) {
        if (chatRoomId == null || chatRoomId <= 0) {
            throw new IllegalArgumentException("chatRoomId 는 양수여야 합니다");
        }
        if (senderId == null || senderId <= 0) {
            throw new IllegalArgumentException("senderId 는 양수여야 합니다");
        }
    }
}
