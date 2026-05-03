package com.sseulang.domain.support.application.dto;

import com.sseulang.domain.support.domain.Inquiry;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.InquiryStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 1:1 문의 단건 결과. 사용자/관리자 공용. presentation Response 가 wrapping.
 *
 * <p>관리자 답변 미작성이면 {@code adminReply}/{@code repliedAt} null. 응답에 그대로 노출.</p>
 */
public record InquiryResult(
        Long id,
        Long userId,
        InquiryCategory category,
        String title,
        String content,
        String email,
        InquiryStatus status,
        List<String> imageUrls,
        String adminReply,
        LocalDateTime repliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InquiryResult from(Inquiry i) {
        return new InquiryResult(
                i.getId(),
                i.getUserId(),
                i.getCategory(),
                i.getTitle(),
                i.getContent(),
                i.getEmail(),
                i.getStatus(),
                i.getImageUrls(),
                i.getAdminReply(),
                i.getRepliedAt(),
                i.getCreatedAt(),
                i.getUpdatedAt()
        );
    }
}
