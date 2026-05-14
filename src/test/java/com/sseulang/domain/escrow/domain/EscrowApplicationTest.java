package com.sseulang.domain.escrow.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * EscrowApplication Aggregate 단위 테스트.
 * 결정 #4 (tradeMode), #5 (feePayer), #6 (cancel), #8 (참여자 가드).
 */
class EscrowApplicationTest {

    private static FeeBreakdown snap(long deliveryFee, long commissionFee, long itemPrice, TradeMode mode) {
        long total = deliveryFee + commissionFee + (mode == TradeMode.INTERNAL ? itemPrice : 0);
        return new FeeBreakdown(new BigDecimal("8.50"), deliveryFee, commissionFee, total, new BigDecimal("0.0500"));
    }

    private static EscrowApplication build(InitiatorRole role, TradeMode mode, FeePayer payer,
                                           long itemPrice, long initiatorShare, long receiverShare) {
        return EscrowApplication.create(
                100L,
                /* initiator */ 11L, /* receiver */ 20L, role,
                mode, payer,
                itemPrice, "맥북",
                "픽업주소", new BigDecimal("37.5"), new BigDecimal("127.0"),
                "도착주소", new BigDecimal("37.6"), new BigDecimal("127.1"),
                Weight.R1TO3, Volume.M, Fragility.F3, null,
                snap(12000L, mode == TradeMode.INTERNAL ? 50_000L : 0L, itemPrice, mode),
                initiatorShare, receiverShare,
                null
        );
    }

    @Test
    @DisplayName("create_initiatorRole_buyer_매핑")
    void create_buyer_role_mapping() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        assertThat(a.getBuyerId()).isEqualTo(11L);   // initiator
        assertThat(a.getSellerId()).isEqualTo(20L);  // receiver
    }

    @Test
    @DisplayName("create_initiatorRole_seller_매핑")
    void create_seller_role_mapping() {
        EscrowApplication a = build(InitiatorRole.seller, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 0L, 1_062_000L);
        assertThat(a.getBuyerId()).isEqualTo(20L);   // receiver
        assertThat(a.getSellerId()).isEqualTo(11L);  // initiator
    }

    @Test
    @DisplayName("markReceiverPaid_initiatorShare_0_즉시_결제완료")
    void receiverPaid_alone_advances_when_initiator_share_zero() {
        // feePayer=buyer 인 경우 seller 부담 0
        EscrowApplication a = build(InitiatorRole.seller, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 0L /* seller initiator share */, 1_062_000L /* buyer receiver share */);
        a.markReceiverPaid();
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.결제완료);
        assertThat(a.getReceiverPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("markReceiverPaid_양쪽share_있으면_결제대기_유지")
    void both_shares_pending_until_both() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.both,
                1_000_000L, 531_000L, 531_000L);
        a.markReceiverPaid();
        // 신청자 결제 안 함 → 결제대기 유지
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.결제대기);
        a.markInitiatorPaid();
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.결제완료);
    }

    @Test
    @DisplayName("markReceiverPaid_2번_INVALID_STATE")
    void receiver_double_pay_rejected() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.both,
                1_000_000L, 531_000L, 531_000L);
        a.markReceiverPaid();
        assertThatThrownBy(a::markReceiverPaid)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("confirmReceipt_buyer만_허용_Mode_B")
    void confirmReceipt_only_buyer_internal() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        a.markInitiatorPaid();  // share 0 receiver + 결제 initiator → 결제완료
        a.markInProgress();     // 라이더 매칭됨

        // seller 가 호출 — 거부
        assertThatThrownBy(() -> a.confirmReceipt(20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);

        // buyer 호출 — 통과
        a.confirmReceipt(11L);
        assertThat(a.getReceiptConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("confirmReceipt_Mode_A_거부")
    void confirmReceipt_external_mode_rejected() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.EXTERNAL, FeePayer.buyer,
                0L, 12_000L, 0L);
        a.markInitiatorPaid();
        a.markInProgress();
        assertThatThrownBy(() -> a.confirmReceipt(11L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("cancel_terminal_상태_거부")
    void cancel_terminal_rejected() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        a.markInitiatorPaid();
        a.markInProgress();
        a.markSettled();  // 완료
        assertThatThrownBy(() -> a.cancel(11L, "변심"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("isParticipant_initiator_or_receiver_만")
    void isParticipant_check() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        assertThat(a.isParticipant(11L)).isTrue();
        assertThat(a.isParticipant(20L)).isTrue();
        assertThat(a.isParticipant(99L)).isFalse();
    }

    // ───── 라운드 14 — 대여 거래대행 lifecycle ─────

    private static EscrowApplication readyForUsing() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        a.markAsRental();
        a.markInitiatorPaid();
        a.markInProgress();
        return a;
    }

    private static EscrowApplication readyForRentalDraft() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        a.markAsRental();
        return a;
    }

    @Test
    @DisplayName("markRentalEnd 정상 — rentalMode=true + 결제 전 시각 기록")
    void markRentalEnd_정상() {
        EscrowApplication a = readyForRentalDraft();
        LocalDateTime endAt = LocalDateTime.now().plusDays(3);

        a.markRentalEnd(endAt);

        assertThat(a.getRentalEndAt()).isEqualTo(endAt);
    }

    @Test
    @DisplayName("markRentalEnd rentalMode 아님_거부")
    void markRentalEnd_일반거래_거부() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);

        assertThatThrownBy(() -> a.markRentalEnd(LocalDateTime.now().plusDays(3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("markAsRental EXTERNAL escrow_거부")
    void markAsRental_external_거부() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.EXTERNAL, FeePayer.buyer,
                0L, 12_000L, 0L);
        assertThatThrownBy(a::markAsRental)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("enterUsing 정상 — 진행중 + rentalMode → 사용중 + usingStartedAt")
    void enterUsing_정상() {
        EscrowApplication a = readyForUsing();
        a.enterUsing();
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.사용중);
        assertThat(a.getUsingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("enterUsing rentalMode 아님_거부")
    void enterUsing_일반거래_거부() {
        EscrowApplication a = build(InitiatorRole.buyer, TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, 1_062_000L, 0L);
        a.markInitiatorPaid();
        a.markInProgress();
        assertThatThrownBy(a::enterUsing)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("requestReturnByBuyer 정상 — 사용중 → 반납중 + returnRequestedAt")
    void requestReturnByBuyer_정상() {
        EscrowApplication a = readyForUsing();
        a.enterUsing();
        a.requestReturnByBuyer(11L);  // initiator buyer
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.반납중);
        assertThat(a.getReturnRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("requestReturnByBuyer seller 호출_FORBIDDEN")
    void requestReturnByBuyer_seller_거부() {
        EscrowApplication a = readyForUsing();
        a.enterUsing();
        assertThatThrownBy(() -> a.requestReturnByBuyer(20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    @Test
    @DisplayName("autoRequestReturn 정상 — 사용중 → 반납중 + returnRequestedAt")
    void autoRequestReturn_정상() {
        EscrowApplication a = readyForRentalDraft();
        a.markRentalEnd(LocalDateTime.now().plusDays(1));
        a.markInitiatorPaid();
        a.markInProgress();
        a.enterUsing();

        LocalDateTime now = LocalDateTime.now();
        a.autoRequestReturn(now);

        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.반납중);
        assertThat(a.getReturnRequestedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("autoRequestReturn 사용중 아님_거부")
    void autoRequestReturn_상태거부() {
        EscrowApplication a = readyForRentalDraft();
        a.markRentalEnd(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> a.autoRequestReturn(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("markSettledAfterReturn 정상 — 반납중 → 완료 + settledAt")
    void markSettledAfterReturn_정상() {
        EscrowApplication a = readyForUsing();
        a.enterUsing();
        a.requestReturnByBuyer(11L);
        a.markSettledAfterReturn();
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.완료);
        assertThat(a.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("markSettled 대여 status 진행중 아님_거부 (대여는 markSettledAfterReturn 사용)")
    void markSettled_사용중_거부() {
        EscrowApplication a = readyForUsing();
        a.enterUsing();  // 사용중 진입
        assertThatThrownBy(a::markSettled)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    // ───── 라운드 14 PR7 — 사용중 단계 양 당사자 합의 취소 ─────

    private static EscrowApplication readyForCancelDuringUsing() {
        EscrowApplication a = readyForUsing();
        a.enterUsing();
        return a;
    }

    @Test
    @DisplayName("requestCancelDuringUsing 정상 — 사용중 + 참여자 + 첫 요청")
    void cancelRequest_정상() {
        EscrowApplication a = readyForCancelDuringUsing();
        a.requestCancelDuringUsing(11L, "사정이 생김");
        assertThat(a.getCancelRequestedBy()).isEqualTo(11L);
        assertThat(a.getCancelRequestedAt()).isNotNull();
        assertThat(a.getCancelReason()).isEqualTo("사정이 생김");
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.사용중);
    }

    @Test
    @DisplayName("requestCancelDuringUsing 비참여자_FORBIDDEN")
    void cancelRequest_비참여자_거부() {
        EscrowApplication a = readyForCancelDuringUsing();
        assertThatThrownBy(() -> a.requestCancelDuringUsing(99L, "외부인"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    @Test
    @DisplayName("requestCancelDuringUsing 이미 요청 중_INVALID_STATE")
    void cancelRequest_중복_거부() {
        EscrowApplication a = readyForCancelDuringUsing();
        a.requestCancelDuringUsing(11L, "1차");
        assertThatThrownBy(() -> a.requestCancelDuringUsing(20L, "2차"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("requestCancelDuringUsing 사용중 아님_INVALID_STATE")
    void cancelRequest_status_거부() {
        EscrowApplication a = readyForUsing();  // 진행중 (사용중 아직 X)
        assertThatThrownBy(() -> a.requestCancelDuringUsing(11L, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("confirmCancelDuringUsing 정상 — 다른 참여자 동의 → 취소")
    void cancelConfirm_정상() {
        EscrowApplication a = readyForCancelDuringUsing();
        a.requestCancelDuringUsing(11L, "사정");
        a.confirmCancelDuringUsing(20L);
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.취소);
        assertThat(a.getCancelledBy()).isEqualTo(20L);
    }

    @Test
    @DisplayName("confirmCancelDuringUsing 요청자 본인_FORBIDDEN")
    void cancelConfirm_본인_거부() {
        EscrowApplication a = readyForCancelDuringUsing();
        a.requestCancelDuringUsing(11L, "사정");
        assertThatThrownBy(() -> a.confirmCancelDuringUsing(11L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    @Test
    @DisplayName("confirmCancelDuringUsing 요청 없음_INVALID_STATE")
    void cancelConfirm_요청없음_거부() {
        EscrowApplication a = readyForCancelDuringUsing();
        assertThatThrownBy(() -> a.confirmCancelDuringUsing(20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_INVALID_STATE);
    }

    @Test
    @DisplayName("withdrawCancelRequest 요청자 본인_정상 클리어")
    void cancelWithdraw_정상() {
        EscrowApplication a = readyForCancelDuringUsing();
        a.requestCancelDuringUsing(11L, "사정");
        a.withdrawCancelRequest(11L);
        assertThat(a.getCancelRequestedBy()).isNull();
        assertThat(a.getCancelRequestedAt()).isNull();
        assertThat(a.getCancelReason()).isNull();
        assertThat(a.getStatus()).isEqualTo(EscrowApplicationStatus.사용중);
    }

    @Test
    @DisplayName("withdrawCancelRequest 요청자 아님_FORBIDDEN")
    void cancelWithdraw_타인_거부() {
        EscrowApplication a = readyForCancelDuringUsing();
        a.requestCancelDuringUsing(11L, "사정");
        assertThatThrownBy(() -> a.withdrawCancelRequest(20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    @Test
    @DisplayName("create_buyer_seller_같음_SELF_NOT_ALLOWED")
    void create_self_rejected() {
        assertThatThrownBy(() -> EscrowApplication.create(
                100L, 11L, 11L, InitiatorRole.buyer,
                TradeMode.INTERNAL, FeePayer.buyer,
                1_000_000L, "x", "p", new BigDecimal("37.5"), new BigDecimal("127.0"),
                "d", new BigDecimal("37.6"), new BigDecimal("127.1"),
                Weight.LT1, Volume.S, Fragility.F1, null,
                snap(12000L, 50000L, 1_000_000L, TradeMode.INTERNAL),
                1_062_000L, 0L, null
        )).isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
    }
}
