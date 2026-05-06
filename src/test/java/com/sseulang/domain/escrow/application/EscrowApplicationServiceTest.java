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
        service = new EscrowApplicationService(
                linkRepo, appRepo, settingsRepo,
                userService, pointService, deliveryRepo, eventPublisher,
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
        EscrowLinkResult r = service.createLink(new EscrowLinkCreateCommand(
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
        assertThatThrownBy(() -> service.createLink(new EscrowLinkCreateCommand(
                99L, InitiatorRole.buyer, FeePayer.both, TradeMode.INTERNAL
        ))).isInstanceOf(BusinessException.class);
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
        EscrowLinkResult link = service.createLink(new EscrowLinkCreateCommand(
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
}
