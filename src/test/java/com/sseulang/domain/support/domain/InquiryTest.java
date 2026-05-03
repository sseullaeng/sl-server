package com.sseulang.domain.support.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryTest {

    private static final Long USER = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 3, 12, 0);

    @Test
    @DisplayName("create_초기 상태 PENDING + adminReply null + replied null")
    void create_초기상태() {
        Inquiry i = Inquiry.create(USER, InquiryCategory.결제, "제목", "본문", "u@x.com", List.of());

        assertThat(i.getStatus()).isEqualTo(InquiryStatus.PENDING);
        assertThat(i.getAdminReply()).isNull();
        assertThat(i.getRepliedAt()).isNull();
        assertThat(i.isOwnedBy(USER)).isTrue();
        assertThat(i.isOwnedBy(99L)).isFalse();
        assertThat(i.isDeletableByOwner()).isTrue();
    }

    @Test
    @DisplayName("create_imageUrls null 안전하게 빈 리스트로 normalize")
    void create_imageUrls_null() {
        Inquiry i = Inquiry.create(USER, InquiryCategory.결제, "제목", "본문", "u@x.com", null);
        assertThat(i.getImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("create_이미지 6장_제한 위반")
    void create_이미지초과_거부() {
        List<String> six = List.of(
                "https://x/1.jpg", "https://x/2.jpg", "https://x/3.jpg",
                "https://x/4.jpg", "https://x/5.jpg", "https://x/6.jpg"
        );
        assertThatThrownBy(() -> Inquiry.create(USER, InquiryCategory.결제, "t", "c", "u@x.com", six))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("writeAdminReply_PENDING 으로 되돌리기 거부")
    void writeAdminReply_PENDING_거부() {
        Inquiry i = Inquiry.create(USER, InquiryCategory.결제, "t", "c", "u@x.com", List.of());

        assertThatThrownBy(() -> i.writeAdminReply("답변", InquiryStatus.PENDING, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("writeAdminReply 후 isDeletableByOwner false")
    void writeAdminReply_삭제불가() {
        Inquiry i = Inquiry.create(USER, InquiryCategory.결제, "t", "c", "u@x.com", List.of());

        i.writeAdminReply("답변", InquiryStatus.DONE, NOW);
        assertThat(i.isDeletableByOwner()).isFalse();
        assertThat(i.getRepliedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("changeStatus_답변 없이 DONE_거부")
    void changeStatus_DONE_답변없음() {
        Inquiry i = Inquiry.create(USER, InquiryCategory.결제, "t", "c", "u@x.com", List.of());

        assertThatThrownBy(() -> i.changeStatus(InquiryStatus.DONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changeStatus_PROCESSING 정상")
    void changeStatus_PROCESSING_정상() {
        Inquiry i = Inquiry.create(USER, InquiryCategory.결제, "t", "c", "u@x.com", List.of());

        i.changeStatus(InquiryStatus.PROCESSING);
        assertThat(i.getStatus()).isEqualTo(InquiryStatus.PROCESSING);
    }
}
