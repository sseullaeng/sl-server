package com.sseulang.domain.escrow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FragilityTest {

    @Test
    @DisplayName("fromCode 는 소문자/대문자 입력을 모두 허용한다")
    void fromCode_case_insensitive() {
        assertThat(Fragility.fromCode("f3")).isEqualTo(Fragility.F3);
        assertThat(Fragility.fromCode("F3")).isEqualTo(Fragility.F3);
        assertThat(Fragility.fromCode("  f3  ")).isEqualTo(Fragility.F3);
    }
}
