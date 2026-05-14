package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateCommand;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationResult;
import com.sseulang.domain.escrow.application.dto.EscrowLinkCreateCommand;
import com.sseulang.domain.escrow.application.dto.EscrowLinkResult;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import com.sseulang.domain.escrow.domain.event.EscrowConfirmedEvent;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EscrowApplicationService 단위 테스트. 보안 영역 — race / idempotent / fee mismatch / share 산정.
 * 게이트 1 검증 영역.
 */
class EscrowApplicationServiceTest {

    private InMemoryFakeEscrowLinkRepository linkRepo;
    private InMemoryFakeEscrowApplicationRepository appRepo;
    private InMemoryFakeEscrowFeeSettingsRepository settingsRepo;
    private com.sseulang.domain.delivery.application.InMemoryFakeDeliveryRepository deliveryRepo;
    private UserApplicationService userService;
    private PointApplicationService pointService;
    private List<Object> publishedEvents;
    private ApplicationEventPublisher eventPublisher;
    private EscrowApplicationService service;

    @BeforeEach
    void setUp() {
        linkRepo = new InMemoryFakeEscrowLinkRepository();
        appRepo = new InMemoryFakeEscrowApplicationRepository();
        settingsRepo = new InMemoryFakeEscrowFeeSettingsRepository();
        userService = mock(UserApplicationService.class);
        pointService = mock(PointApplicationService.class);
        publishedEvents = new ArrayList<>();
        eventPublisher = publishedEvents::add;

        // requireVerified — 통과
        doNothing().when(userService).requireVerified(anyLong());
        // getById — User stub
        User stub = mock(User.class);
        when(stub.getNickname()).thenReturn("닉넴");
        when(userService.getById(anyLong())).thenReturn(stub);

        deliveryRepo = new com.sseulang.domain.delivery.application.InMemoryFakeDeliveryRepository();
        // 라운드 12 PR-B-3 — 내부 흐름 의존 추가 (외부 link 흐름 테스트엔 미사용 → mock 으로만 stub)
        com.sseulang.domain.chat.application.ChatRoomApplicationService chatRoomService =
                mock(com.sseulang.domain.chat.application.ChatRoomApplicationService.class);
        com.sseulang.domain.item.application.ItemApplicationService itemAppService =
                mock(com.sseulang.domain.item.application.ItemApplicationService.class);
        com.sseulang.domain.transaction.application.TransactionApplicationService txAppService =
                mock(com.sseulang.domain.transaction.application.TransactionApplicationService.class);
        com.sseulang.domain.transaction.application.TransactionCascadeService txCascadeService =
                mock(com.sseulang.domain.transaction.application.TransactionCascadeService.class);
        com.sseulang.domain.notification.application.NotificationApplicationService notifService =
                mock(com.sseulang.domain.notification.application.NotificationApplicationService.class);
        service = new EscrowApplicationService(
                linkRepo, appRepo, settingsRepo,
                userService, pointService, deliveryRepo, eventPublisher,
                chatRoomService, itemAppService, txAppService, txCascadeService, notifService,
                24
        );
    }

    private EscrowApplicationCreateCommand validForm(Long receiverId, String linkToken,
                                                     long itemPrice, long deliveryFee, long commissionFee, long totalFee) {
        return new EscrowApplicationCreateCommand(
                receiverId, linkToken,
                itemPrice, "맥북 프로",
                "픽업주소", new BigDecimal("37.5065"), new BigDecimal("127.0530"),
                "도착주소", new BigDecimal("37.5144"), new BigDecimal("127.1058"),
                Weight.R1TO3, Volume.M, Fragility.F3, "메모",
                deliveryFee, commissionFee, totalFee, new BigDecimal("8.50"),
                List.of()
        );
    }

    // ------------------------------- createLink -------------------------------

    @Test
    @DisplayName("createLink_정상_token+expiresAt_발급")
    void createLink_normal() {
        EscrowLinkResult r = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        assertThat(r.linkToken()).hasSize(36);
        assertThat(r.initiatorRole()).isEqualTo(InitiatorRole.buyer);
        assertThat(r.expiresAt()).isAfter(java.time.LocalDateTime.now().plusHours(23));
    }

    @Test
    @DisplayName("createLink_미인증_requireVerified_차단")
    void createLink_unverified_blocked() {
        doThrow(new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED))
                .when(userService).requireVerified(99L);
        assertThatThrownBy(() -> service.createLink(EscrowLinkCreateCommand.legacy(
                99L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ))).isInstanceOf(BusinessException.class);
    }

    // ------------------------------- previewFee -------------------------------

    @Test
    @DisplayName("previewFee_BOTH_buyer/seller_50:50_분담")
    void previewFee_both_5050() {
        com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewResult r =
                service.previewFee(new com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewCommand(
                        TradeMode.INTERNAL, 30_000L,
                        new BigDecimal("37.5065"), new BigDecimal("127.0530"),
                        new BigDecimal("37.5144"), new BigDecimal("127.1058"),
                        Weight.R1TO3, Volume.M, Fragility.F3, FeePayer.both
                ));
        assertThat(r.deliveryFee()).isPositive();
        assertThat(r.commissionFee()).isPositive();
        long feeTotal = r.deliveryFee() + r.commissionFee();
        // BOTH: buyer = item + fee/2, seller = fee - fee/2
        assertThat(r.buyerPayable()).isEqualTo(30_000L + feeTotal / 2);
        assertThat(r.sellerPayable()).isEqualTo(feeTotal - feeTotal / 2);
    }

    @Test
    @DisplayName("previewFee_BUYER_단독_buyer가_전액_부담")
    void previewFee_buyer_only() {
        com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewResult r =
                service.previewFee(new com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewCommand(
                        TradeMode.INTERNAL, 30_000L,
                        new BigDecimal("37.5065"), new BigDecimal("127.0530"),
                        new BigDecimal("37.5144"), new BigDecimal("127.1058"),
                        Weight.R1TO3, Volume.M, Fragility.F3, FeePayer.buyer
                ));
        long feeTotal = r.deliveryFee() + r.commissionFee();
        assertThat(r.buyerPayable()).isEqualTo(30_000L + feeTotal);
        assertThat(r.sellerPayable()).isZero();
    }

    @Test
    @DisplayName("previewFee_필수_누락_ESCROW_FORM_INVALID")
    void previewFee_invalid() {
        assertThatThrownBy(() -> service.previewFee(
                new com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewCommand(
                        null, 30_000L,
                        new BigDecimal("37.5065"), new BigDecimal("127.0530"),
                        new BigDecimal("37.5144"), new BigDecimal("127.1058"),
                        Weight.R1TO3, Volume.M, Fragility.F3, FeePayer.both
                )
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ESCROW_FORM_INVALID);
    }

    // ------------------------------ createApplication ------------------------------

    /** 거리 8.51km / weight=1to3 / volume=m / fragility=f3 / itemPrice=1.8M 시점의 산정값 (default settings 기준). */
    private long expectedDeliveryFee = -1; // setUp 후 한번 산정해서 캐시
    private long expectedCommissionFee = -1;
    private long expectedTotalFee = -1;

    private void cacheExpectedFees() {
        if (expectedDeliveryFee > 0) return;
        var settings = settingsRepo.findSingleton();
        // 백엔드가 좌표로 재계산하는 distance 와 동일 산정 (validForm 의 좌표 사용)
        BigDecimal dist = com.sseulang.domain.escrow.domain.EscrowFeeCalculator.distanceKm(
                37.5065, 127.0530, 37.5144, 127.1058
        );
        var fb = com.sseulang.domain.escrow.domain.EscrowFeeCalculator.calculate(
                settings, TradeMode.INTERNAL, 1_800_000L, dist,
                Weight.R1TO3, Volume.M, Fragility.F3
        );
        expectedDeliveryFee = fb.deliveryFee();
        expectedCommissionFee = fb.commissionFee();
        expectedTotalFee = fb.totalFee();
    }

    @Test
    @DisplayName("createApplication_정상_status_결제대기_+_share_산정")
    void createApplication_normal() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult r = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        assertThat(r.status()).isEqualTo(EscrowApplicationStatus.결제대기);
        assertThat(r.buyerId()).isEqualTo(11L);
        assertThat(r.sellerId()).isEqualTo(20L);
        // both → buyer (initiator) 부담 = itemPrice + (delivery+commission)/2, seller 부담 = (delivery+commission) - half
        long feeTotal = expectedDeliveryFee + expectedCommissionFee;
        assertThat(r.initiatorShare()).isEqualTo(1_800_000L + feeTotal / 2);
        assertThat(r.receiverShare()).isEqualTo(feeTotal - feeTotal / 2);
    }

    @Test
    @DisplayName("createApplication_본인_SELF_NOT_ALLOWED")
    void createApplication_self_rejected() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        assertThatThrownBy(() -> service.createApplication(validForm(
                11L /* self */, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ))).isInstanceOf(BusinessException.class)
           .extracting(e -> ((BusinessException) e).getErrorCode())
           .isEqualTo(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
    }

    @Test
    @DisplayName("createApplication_idempotent_본인_더블클릭_같은application")
    void createApplication_idempotent() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult first = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        EscrowApplicationResult second = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    @DisplayName("createApplication_race_다른receiver_ALREADY_TAKEN")
    void createApplication_race_other_receiver() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        // 첫 수신자 확정
        service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        // 다른 사용자 시도 → 실패
        assertThatThrownBy(() -> service.createApplication(validForm(
                30L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ))).isInstanceOf(BusinessException.class)
           .extracting(e -> ((BusinessException) e).getErrorCode())
           .isEqualTo(ErrorCode.ESCROW_LINK_ALREADY_TAKEN);
    }

    @Test
    @DisplayName("createApplication_fee_위변조_FEE_MISMATCH")
    void createApplication_fee_tampering_rejected() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        // 100원 차이 — tolerance 초과
        assertThatThrownBy(() -> service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee - 100, expectedCommissionFee, expectedTotalFee
        ))).isInstanceOf(BusinessException.class)
           .extracting(e -> ((BusinessException) e).getErrorCode())
           .isEqualTo(ErrorCode.ESCROW_FEE_MISMATCH);
    }

    // ------------------------------- recordPaymentConfirmed -------------------------------

    @Test
    @DisplayName("recordPaymentConfirmed_양쪽결제완료_EscrowConfirmedEvent_발행")
    void recordPaymentConfirmed_event_published() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        // receiver 결제 — 양쪽 share 있으니 결제대기 유지
        service.recordPaymentConfirmed(app.id(), 20L);
        assertThat(publishedEvents).isEmpty();
        // initiator 결제 — 양쪽 충족 → 결제완료 + 이벤트
        service.recordPaymentConfirmed(app.id(), 11L);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(EscrowConfirmedEvent.class);
        EscrowConfirmedEvent ev = (EscrowConfirmedEvent) publishedEvents.get(0);
        assertThat(ev.escrowApplicationId()).isEqualTo(app.id());
    }

    // ------------------------------- verifyChargeIntent -------------------------------

    @Test
    @DisplayName("verifyChargeIntent_본인_share_일치_통과")
    void verifyChargeIntent_match_pass() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        // receiver share 만큼 결제 시도
        assertThatNoException().isThrownBy(() ->
                service.verifyChargeIntent(app.id(), 20L, app.receiverShare())
        );
    }

    @Test
    @DisplayName("verifyChargeIntent_share_불일치_FEE_MISMATCH")
    void verifyChargeIntent_amount_mismatch() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        assertThatThrownBy(() -> service.verifyChargeIntent(app.id(), 20L, app.receiverShare() + 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FEE_MISMATCH);
    }

    @Test
    @DisplayName("verifyChargeIntent_제3자_FORBIDDEN")
    void verifyChargeIntent_other_user_rejected() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        assertThatThrownBy(() -> service.verifyChargeIntent(app.id(), 99L, 1000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    // ------------------------------- cancel -------------------------------

    @Test
    @DisplayName("cancel_매칭전_status_취소")
    void cancel_before_match() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        service.cancel(app.id(), 11L, "단순 변심");
        EscrowApplicationResult after = service.getById(app.id(), 11L);
        assertThat(after.status()).isEqualTo(EscrowApplicationStatus.취소);
        assertThat(after.cancelledBy()).isEqualTo(11L);
    }

    @Test
    @DisplayName("cancel_제3자_FORBIDDEN")
    void cancel_third_party_rejected() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        assertThatThrownBy(() -> service.cancel(app.id(), 99L, "남이 취소"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    // ------------------------------- previewPayShare (PR-E) -------------------------------

    @Test
    @DisplayName("previewPayShare_잔액_충분_canPay=true_deficit=0")
    void previewPayShare_canPay() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        long receiverShare = app.receiverShare();
        when(userService.getPointSnapshot(20L))
                .thenReturn(new com.sseulang.domain.user.domain.UserRepository.PointSnapshot(receiverShare + 1000, 0));

        var preview = service.previewPayShare(app.id(), 20L);

        assertThat(preview.myShare()).isEqualTo(receiverShare);
        assertThat(preview.myBalance()).isEqualTo(receiverShare + 1000);
        assertThat(preview.deficit()).isZero();
        assertThat(preview.canPay()).isTrue();
        assertThat(preview.alreadyPaid()).isFalse();
    }

    @Test
    @DisplayName("previewPayShare_잔액_부족_deficit_정확_canPay=false")
    void previewPayShare_deficit() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        long initiatorShare = app.initiatorShare();
        when(userService.getPointSnapshot(11L))
                .thenReturn(new com.sseulang.domain.user.domain.UserRepository.PointSnapshot(initiatorShare - 500, 0));

        var preview = service.previewPayShare(app.id(), 11L);

        assertThat(preview.myShare()).isEqualTo(initiatorShare);
        assertThat(preview.deficit()).isEqualTo(500L);
        assertThat(preview.canPay()).isFalse();
    }

    @Test
    @DisplayName("previewPayShare_제3자_FORBIDDEN")
    void previewPayShare_third_party_rejected() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        assertThatThrownBy(() -> service.previewPayShare(app.id(), 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ESCROW_FORBIDDEN);
    }

    @Test
    @DisplayName("previewPayShare_이미_본인_share_결제_완료_alreadyPaid=true_canPay=false")
    void previewPayShare_already_paid() {
        cacheExpectedFees();
        EscrowLinkResult link = service.createLink(EscrowLinkCreateCommand.legacy(
                11L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ));
        EscrowApplicationResult app = service.createApplication(validForm(
                20L, link.linkToken(), 1_800_000L,
                expectedDeliveryFee, expectedCommissionFee, expectedTotalFee
        ));
        service.recordPaymentConfirmed(app.id(), 20L);

        long receiverShare = app.receiverShare();
        when(userService.getPointSnapshot(20L))
                .thenReturn(new com.sseulang.domain.user.domain.UserRepository.PointSnapshot(receiverShare * 10, 0));

        var preview = service.previewPayShare(app.id(), 20L);

        assertThat(preview.alreadyPaid()).isTrue();
        assertThat(preview.canPay()).isFalse();
    }
}
