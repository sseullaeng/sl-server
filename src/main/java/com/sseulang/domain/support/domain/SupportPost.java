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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SupportPost Aggregate Root — FAQ / QNA 공개 게시글. V12 {@code support_posts} 매핑.
 *
 * <p>관리자만 작성/수정/삭제. 사용자는 비로그인 포함 누구나 조회. 상태 개념 없음 — 작성 즉시 노출.</p>
 *
 * <p>FAQ 와 QNA 가 컬럼이 동일해 단일 테이블 + {@code post_type} 컬럼으로 구분.</p>
 */
@Entity
@Table(name = "support_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportPost extends BaseEntity {

    public static final int IMAGE_MAX_COUNT = 5;
    private static final int QUESTION_MAX_LENGTH = 500;
    private static final int IMAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 10)
    private SupportPostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private InquiryCategory category;

    @Column(name = "question", nullable = false, length = QUESTION_MAX_LENGTH)
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "image_urls", columnDefinition = "JSON")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "admin_id")
    private Long adminId;

    public static SupportPost create(
            Long adminId,
            SupportPostType postType,
            InquiryCategory category,
            String question,
            String answer,
            List<String> imageUrls
    ) {
        if (postType == null) {
            throw new IllegalArgumentException("postType 은 필수입니다");
        }
        if (category == null) {
            throw new IllegalArgumentException("category 는 필수입니다");
        }
        validateText(question, "question", QUESTION_MAX_LENGTH);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer 는 필수입니다");
        }
        List<String> sanitized = sanitizeImages(imageUrls);

        SupportPost p = new SupportPost();
        p.adminId = adminId;
        p.postType = postType;
        p.category = category;
        p.question = question.trim();
        p.answer = answer;
        p.imageUrls = sanitized;
        return p;
    }

    public void update(
            SupportPostType postType,
            InquiryCategory category,
            String question,
            String answer,
            List<String> imageUrls
    ) {
        if (postType == null) {
            throw new IllegalArgumentException("postType 은 필수입니다");
        }
        if (category == null) {
            throw new IllegalArgumentException("category 는 필수입니다");
        }
        validateText(question, "question", QUESTION_MAX_LENGTH);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer 는 필수입니다");
        }
        this.postType = postType;
        this.category = category;
        this.question = question.trim();
        this.answer = answer;
        this.imageUrls = sanitizeImages(imageUrls);
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
