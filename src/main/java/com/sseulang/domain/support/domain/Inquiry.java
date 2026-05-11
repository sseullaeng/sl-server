package com.sseulang.domain.support.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.common.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    public static final int IMAGE_MAX_COUNT = 5;
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int EMAIL_MAX_LENGTH = 255;
    private static final int IMAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private InquiryCategory category;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "email", nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InquiryStatus status;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "image_urls", columnDefinition = "JSON")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    public static Inquiry create(
            Long userId,
            InquiryCategory category,
            String title,
            String content,
            String email,
            List<String> imageUrls
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 는 필수입니다");
        }
        if (category == null) {
            throw new IllegalArgumentException("category 는 필수입니다");
        }
        validateText(title, "title", TITLE_MAX_LENGTH);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 는 필수입니다");
        }
        validateEmail(email);
        List<String> sanitized = sanitizeImages(imageUrls);

        Inquiry i = new Inquiry();
        i.userId = userId;
        i.category = category;
        i.title = title.trim();
        i.content = content;            
        i.email = email.trim();
        i.status = InquiryStatus.PENDING;
        i.imageUrls = sanitized;
        return i;
    }

    
    public void writeAdminReply(String adminReply, InquiryStatus newStatus, LocalDateTime now) {
        if (adminReply == null || adminReply.isBlank()) {
            throw new IllegalArgumentException("adminReply 는 필수입니다");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("status 는 필수입니다");
        }
        if (newStatus == InquiryStatus.PENDING) {
            
            throw new IllegalArgumentException("답변 작성 시 PENDING 으로 되돌릴 수 없습니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        this.adminReply = adminReply;
        this.status = newStatus;
        this.repliedAt = now;
    }

    

    public void changeStatus(InquiryStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status 는 필수입니다");
        }
        
        if (newStatus == InquiryStatus.PENDING && this.status != InquiryStatus.PENDING) {
            throw new IllegalArgumentException("PENDING 으로 되돌릴 수 없습니다");
        }
        if (newStatus == InquiryStatus.PROCESSING && this.status == InquiryStatus.DONE) {
            throw new IllegalArgumentException("DONE 에서 PROCESSING 으로 되돌릴 수 없습니다");
        }
        
        if (newStatus == InquiryStatus.DONE && (adminReply == null || adminReply.isBlank())) {
            throw new IllegalArgumentException("답변 없이 DONE 으로 변경할 수 없습니다");
        }
        this.status = newStatus;
    }

    
    public boolean isOwnedBy(Long candidateUserId) {
        return candidateUserId != null && candidateUserId.equals(userId);
    }

    
    public boolean isDeletableByOwner() {
        return status == InquiryStatus.PENDING;
    }

    public List<String> getImageUrls() {
        return imageUrls == null ? Collections.emptyList() : Collections.unmodifiableList(imageUrls);
    }

    private static void validateText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 은 필수입니다");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " 은 " + maxLength + "자 이하여야 합니다");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email 은 필수입니다");
        }
        String trimmed = email.trim();
        if (trimmed.length() > EMAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("email 은 " + EMAIL_MAX_LENGTH + "자 이하여야 합니다");
        }
        
    }

    private static List<String> sanitizeImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return new ArrayList<>();
        }
        if (imageUrls.size() > IMAGE_MAX_COUNT) {
            throw new IllegalArgumentException("이미지는 최대 " + IMAGE_MAX_COUNT + "장입니다");
        }
        List<String> result = new ArrayList<>(imageUrls.size());
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("imageUrl 은 비어 있을 수 없습니다");
            }
            String trimmed = url.trim();
            if (trimmed.length() > IMAGE_URL_MAX_LENGTH) {
                throw new IllegalArgumentException("imageUrl 은 " + IMAGE_URL_MAX_LENGTH + "자 이하여야 합니다");
            }
            result.add(trimmed);
        }
        return result;
    }
}
