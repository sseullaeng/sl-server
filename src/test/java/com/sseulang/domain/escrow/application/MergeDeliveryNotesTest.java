package com.sseulang.domain.escrow.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MergeDeliveryNotesTest {

    @Test
    @DisplayName("양쪽 모두 있음_개행으로 머지")
    void 양쪽_머지() {
        String r = EscrowApplicationService.mergeDeliveryNotes("판매자: 픽업 시 노크", "구매자: 경비실 맡겨주세요");
        assertThat(r).isEqualTo("판매자: 픽업 시 노크\n\n구매자: 경비실 맡겨주세요");
    }

    @Test
    @DisplayName("발급자만_그대로")
    void 발급자만() {
        assertThat(EscrowApplicationService.mergeDeliveryNotes("판매자: 픽업 시 노크", null))
                .isEqualTo("판매자: 픽업 시 노크");
        assertThat(EscrowApplicationService.mergeDeliveryNotes("판매자: 픽업 시 노크", "  "))
                .isEqualTo("판매자: 픽업 시 노크");
    }

    @Test
    @DisplayName("수신자만_그대로")
    void 수신자만() {
        assertThat(EscrowApplicationService.mergeDeliveryNotes(null, "구매자: 경비실"))
                .isEqualTo("구매자: 경비실");
    }

    @Test
    @DisplayName("둘 다 없음_null")
    void 양쪽_없음() {
        assertThat(EscrowApplicationService.mergeDeliveryNotes(null, null)).isNull();
        assertThat(EscrowApplicationService.mergeDeliveryNotes("", "")).isNull();
    }

    @Test
    @DisplayName("머지 결과 500자 초과_500자로 컷")
    void 길이제한() {
        String a = "a".repeat(300);
        String b = "b".repeat(300);
        String r = EscrowApplicationService.mergeDeliveryNotes(a, b);
        assertThat(r).hasSize(500);
        assertThat(r).startsWith(a);
    }
}
