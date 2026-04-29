package com.sseulang.domain.message.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTest {

    @Test
    @DisplayName("text 정상")
    void text_정상() {
        Message m = Message.text(1L, 100L, "안녕하세요");
        assertThat(m.getChatRoomId()).isEqualTo(1L);
        assertThat(m.getSenderId()).isEqualTo(100L);
        assertThat(m.getContent()).isEqualTo("안녕하세요");
        assertThat(m.getImageUrls()).isEmpty();
        assertThat(m.isImageMessage()).isFalse();
        assertThat(m.preview()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("text 빈 content_거부")
    void text_빈_content_거부() {
        assertThatThrownBy(() -> Message.text(1L, 100L, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Message.text(1L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Message.text(1L, 100L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("text content 2000자 초과_거부")
    void text_길이초과_거부() {
        String tooLong = "가".repeat(2001);
        assertThatThrownBy(() -> Message.text(1L, 100L, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("image 정상_preview 는 이미지 placeholder")
    void image_정상() {
        Message m = Message.image(1L, 100L, List.of("https://img/1", "https://img/2"));
        assertThat(m.isImageMessage()).isTrue();
        assertThat(m.getImageUrls()).hasSize(2);
        assertThat(m.preview()).isEqualTo("[사진 2장]");

        Message single = Message.image(1L, 100L, List.of("https://img/x"));
        assertThat(single.preview()).isEqualTo("[사진]");
    }

    @Test
    @DisplayName("image 빈 imageUrls_거부")
    void image_빈_거부() {
        assertThatThrownBy(() -> Message.image(1L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Message.image(1L, 100L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("image 6장 초과_거부")
    void image_6장_거부() {
        List<String> tooMany = List.of("a", "b", "c", "d", "e", "f");
        assertThatThrownBy(() -> Message.image(1L, 100L, tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null/비양수 ID_거부")
    void invalid_id_거부() {
        assertThatThrownBy(() -> Message.text(null, 1L, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Message.text(1L, 0L, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Message.image(-1L, 1L, List.of("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
