package com.sseulang.domain.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    @DisplayName("Email 정상값_생성")
    void 정상값_생성() {
        Email e = new Email("foo@example.com");
        assertThat(e.value()).isEqualTo("foo@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "@nope.com", "no@domain", "no@.com", "spaces in@x.com"})
    @DisplayName("Email 잘못된 형식_예외")
    void 잘못된_형식_예외(String invalid) {
        assertThatThrownBy(() -> new Email(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Email null/blank_예외")
    void null_blank_예외() {
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Email 100자 초과_예외")
    void 길이초과_예외() {
        String longLocal = "a".repeat(95);
        String over = longLocal + "@x.com";  // 95+6 = 101
        assertThatThrownBy(() -> new Email(over))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Email 대소문자 차이_정규화로 동일 취급")
    void 대소문자_정규화() {
        Email lower = new Email("user@example.com");
        Email mixed = new Email("User@Example.COM");
        assertThat(mixed).isEqualTo(lower);
        assertThat(mixed.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Email 양쪽 공백_trim")
    void 공백_trim() {
        Email e = new Email("  user@example.com  ");
        assertThat(e.value()).isEqualTo("user@example.com");
    }
}
