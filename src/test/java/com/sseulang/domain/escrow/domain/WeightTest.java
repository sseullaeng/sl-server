package com.sseulang.domain.escrow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightTest {

    @Test
    @DisplayName("fromCode 정식 코드 매칭")
    void fromCode_정식() {
        assertThat(Weight.fromCode("lt1")).isEqualTo(Weight.LT1);
        assertThat(Weight.fromCode("1to3")).isEqualTo(Weight.R1TO3);
        assertThat(Weight.fromCode("3to5")).isEqualTo(Weight.R3TO5);
        assertThat(Weight.fromCode("5to10")).isEqualTo(Weight.R5TO10);
        assertThat(Weight.fromCode("gt10")).isEqualTo(Weight.GT10);
    }

    @Test
    @DisplayName("fromCode 프론트 alias — under1/over10 호환")
    void fromCode_alias() {
        assertThat(Weight.fromCode("under1")).isEqualTo(Weight.LT1);
        assertThat(Weight.fromCode("over10")).isEqualTo(Weight.GT10);
    }

    @Test
    @DisplayName("fromCode 대소문자/공백 normalize")
    void fromCode_normalize() {
        assertThat(Weight.fromCode("OVER10")).isEqualTo(Weight.GT10);
        assertThat(Weight.fromCode(" gt10 ")).isEqualTo(Weight.GT10);
        assertThat(Weight.fromCode("LT1")).isEqualTo(Weight.LT1);
    }

    @Test
    @DisplayName("fromCode 무효 / null 거부")
    void fromCode_거부() {
        assertThatThrownBy(() -> Weight.fromCode("foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Weight");
        assertThatThrownBy(() -> Weight.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
