package com.sseulang.domain.escrow.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * EscrowLink Aggregate 단위 테스트. 결정 #2/3 — claimByReceiver 가드.
 */
class EscrowLinkTest {

    private static EscrowLink link(Long initiatorId) {
        return EscrowLink.create(initiatorId, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL, 24);
    }

    @Test
    @DisplayName("claimByReceiver_본인_SELF_NOT_ALLOWED")
    void claim_self_blocked() {
        EscrowLink l = link(11L);
        assertThatThrownBy(() -> l.claimByReceiver(11L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
    }

    @Test
    @DisplayName("claimByReceiver_정상_receiver_확정")
    void claim_normal() {
        EscrowLink l = link(11L);
        l.claimByReceiver(20L);
        assertThat(l.getReceiverId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("claimByReceiver_만료_LINK_EXPIRED")
    void claim_expired() {
        EscrowLink l = link(11L);
        ReflectionTestUtils.setField(l, "expiresAt", LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> l.claimByReceiver(20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_LINK_EXPIRED);
    }

    @Test
    @DisplayName("claimByReceiver_이미다른수신자_ALREADY_TAKEN")
    void claim_already_taken_other() {
        EscrowLink l = link(11L);
        l.claimByReceiver(20L);  // 첫 수신자
        assertThatThrownBy(() -> l.claimByReceiver(30L))  // 다른 수신자 시도
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_LINK_ALREADY_TAKEN);
    }

    @Test
    @DisplayName("claimByReceiver_같은수신자재시도_idempotent")
    void claim_same_receiver_idempotent() {
        EscrowLink l = link(11L);
        l.claimByReceiver(20L);
        // 같은 사용자 재시도 — 가드 통과 (idempotent — Aggregate 측면).
        // 실 흐름의 idempotent 처리는 ApplicationService 가 더 위에서 처리.
        assertThatNoException().isThrownBy(() -> l.claimByReceiver(20L));
    }

    @Test
    @DisplayName("markAsCompleted_대기상태만_허용")
    void completed_only_from_대기() {
        EscrowLink l = link(11L);
        l.markAsCompleted();
        assertThat(l.getStatus()).isEqualTo(EscrowLinkStatus.완료);
        // 두번째 호출은 INVALID_STATE
        assertThatThrownBy(l::markAsCompleted)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("markAsCancelled_대기상태만_허용")
    void cancel_only_from_대기() {
        EscrowLink l = link(11L);
        l.markAsCancelled();
        assertThat(l.getStatus()).isEqualTo(EscrowLinkStatus.취소);
    }

    @Test
    @DisplayName("create_expiryHours_0이하_거부")
    void create_invalid_expiry() {
        assertThatThrownBy(() -> EscrowLink.create(11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
