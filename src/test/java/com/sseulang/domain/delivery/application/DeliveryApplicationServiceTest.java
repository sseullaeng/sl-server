package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.application.dto.DeliveryCreateCommand;
import com.sseulang.domain.delivery.application.dto.DeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeliveryApplicationServiceTest {

    private static final Long REQUESTER = 100L;
    private static final Long RIDER = 200L;

    private InMemoryFakeDeliveryRepository repo;
    private UserApplicationService userService;
    private PointApplicationService pointService;
    private com.sseulang.domain.escrow.domain.EscrowApplicationRepository escrowApplicationRepository;
    private DeliveryApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeDeliveryRepository();
        userService = mock(UserApplicationService.class);
        pointService = mock(PointApplicationService.class);
        com.sseulang.domain.delivery.domain.DeliveryLocationCache locationCache =
                mock(com.sseulang.domain.delivery.domain.DeliveryLocationCache.class);
        escrowApplicationRepository = mock(com.sseulang.domain.escrow.domain.EscrowApplicationRepository.class);
        service = new DeliveryApplicationService(repo, userService, pointService, locationCache, e -> {}, escrowApplicationRepository);
    }

    @Test
    @DisplayName("create 정상_requireVerified 호출 + 모집중 저장")
    void create_정상() {
        DeliveryResult r = service.create(newCommand());

        verify(userService).requireVerified(REQUESTER);
        assertThat(r.requesterId()).isEqualTo(REQUESTER);
        assertThat(r.status()).isEqualTo(DeliveryStatus.모집중);
        assertThat(r.fee()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("create 미인증_requireVerified throw")
    void create_미인증_차단() {
        doThrow(new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED))
                .when(userService).requireVerified(REQUESTER);

        assertThatThrownBy(() -> service.create(newCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("accept 정상_모집중→수락 + riderId 세팅")
    void accept_정상() {
        DeliveryResult created = service.create(newCommand());

        DeliveryResult r = service.accept(created.id(), RIDER);

        verify(userService).requireVerified(RIDER);
        assertThat(r.status()).isEqualTo(DeliveryStatus.수락);
        assertThat(r.riderId()).isEqualTo(RIDER);
        assertThat(r.acceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("accept 본인 거래_DELIVERY_SELF_NOT_ALLOWED")
    void accept_본인_차단() {
        DeliveryResult created = service.create(newCommand());

        assertThatThrownBy(() -> service.accept(created.id(), REQUESTER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_SELF_NOT_ALLOWED);
    }

    @Test
    @DisplayName("accept 이미 수락된 요청_DELIVERY_ALREADY_ACCEPTED")
    void accept_race_loser() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        assertThatThrownBy(() -> service.accept(created.id(), 300L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
    }

    @Test
    @DisplayName("accept 없는 요청_DELIVERY_NOT_FOUND")
    void accept_not_found() {
        assertThatThrownBy(() -> service.accept(999L, RIDER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    @DisplayName("markPickedUp 정상_수락→배송중 (라이더만)")
    void pickup_정상() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        DeliveryResult r = service.markPickedUp(created.id(), RIDER);

        assertThat(r.status()).isEqualTo(DeliveryStatus.배송중);
        assertThat(r.pickedUpAt()).isNotNull();
    }

    @Test
    @DisplayName("markPickedUp 라이더 아닌 사용자_DELIVERY_FORBIDDEN")
    void pickup_타인_차단() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        assertThatThrownBy(() -> service.markPickedUp(created.id(), 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_FORBIDDEN);
    }

    @Test
    @DisplayName("complete 정상_정산 호출 + 배송완료→정산완료")
    void complete_정상() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);
        service.markPickedUp(created.id(), RIDER);
        service.markDelivered(created.id(), RIDER);

        DeliveryResult r = service.complete(created.id(), REQUESTER);

        verify(pointService).transferForDelivery(
                eq(REQUESTER), eq(RIDER), eq(5000L), eq(created.id()), anyString()
        );
        assertThat(r.status()).isEqualTo(DeliveryStatus.정산완료);
        assertThat(r.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete 라이더 시도_DELIVERY_FORBIDDEN (요청자만)")
    void complete_타인_차단() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);
        service.markPickedUp(created.id(), RIDER);
        service.markDelivered(created.id(), RIDER);

        assertThatThrownBy(() -> service.complete(created.id(), RIDER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_FORBIDDEN);

        verify(pointService, never()).transferForDelivery(anyLong(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("complete 배송완료 아닌 상태_DELIVERY_INVALID_STATE + 잔액 이동 안 함")
    void complete_invalid_state() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        // 수락 상태에서 정산 시도
        assertThatThrownBy(() -> service.complete(created.id(), REQUESTER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);

        verify(pointService, never()).transferForDelivery(anyLong(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("complete 잔액 부족_INSUFFICIENT_POINT 전파 + 상태 전이 안 일어남")
    void complete_잔액부족() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);
        service.markPickedUp(created.id(), RIDER);
        service.markDelivered(created.id(), RIDER);

        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_POINT))
                .when(pointService).transferForDelivery(
                        anyLong(), anyLong(), anyLong(), anyLong(), anyString());

        assertThatThrownBy(() -> service.complete(created.id(), REQUESTER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        // 트랜잭션 롤백 가정 — fake repo 라 검증 한계가 있지만,
        // 적어도 markSettled 이전에 transfer 가 throw 됐는지 확인 (배송완료 그대로)
        DeliveryResult after = service.getById(created.id(), REQUESTER);
        assertThat(after.status()).isEqualTo(DeliveryStatus.배송완료);
    }

    @Test
    @DisplayName("cancel 모집중_취소")
    void cancel_정상() {
        DeliveryResult created = service.create(newCommand());

        DeliveryResult r = service.cancel(created.id(), REQUESTER, "변심");

        assertThat(r.status()).isEqualTo(DeliveryStatus.취소);
        assertThat(r.cancelReason()).isEqualTo("변심");
    }

    @Test
    @DisplayName("cancel 라이더 시도_DELIVERY_FORBIDDEN")
    void cancel_타인_차단() {
        DeliveryResult created = service.create(newCommand());

        assertThatThrownBy(() -> service.cancel(created.id(), 999L, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_FORBIDDEN);
    }

    @Test
    @DisplayName("cancel 수락 이후_DELIVERY_INVALID_STATE")
    void cancel_수락이후_차단() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        assertThatThrownBy(() -> service.cancel(created.id(), REQUESTER, "변심"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);
    }

    @Test
    @DisplayName("getById 모집중_누구나 조회 가능")
    void getById_모집중_공개() {
        DeliveryResult created = service.create(newCommand());

        DeliveryResult r = service.getById(created.id(), 999L);

        assertThat(r.id()).isEqualTo(created.id());
    }

    @Test
    @DisplayName("getById 수락 이후_참여자만 조회 가능")
    void getById_수락이후_제한() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        // 외부인은 거부
        assertThatThrownBy(() -> service.getById(created.id(), 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_FORBIDDEN);

        // 요청자/라이더는 통과
        assertThat(service.getById(created.id(), REQUESTER).id()).isEqualTo(created.id());
        assertThat(service.getById(created.id(), RIDER).id()).isEqualTo(created.id());
    }

    @Test
    @DisplayName("getById 수락 이후_연결된 escrow buyer/seller 도 조회 가능")
    void getById_수락이후_escrow참여자_허용() {
        long escrowId = 7000L;
        long sellerId = 555L;
        long buyerId = REQUESTER;
        com.sseulang.domain.delivery.domain.DeliveryRequest seed =
                com.sseulang.domain.delivery.domain.DeliveryRequest.createFromEscrow(
                        REQUESTER, escrowId, "픽업", "배송", "물품", 5000L, LocalDateTime.now());
        repo.save(seed);
        service.accept(seed.getId(), RIDER);

        com.sseulang.domain.escrow.domain.EscrowApplication app =
                mock(com.sseulang.domain.escrow.domain.EscrowApplication.class);
        org.mockito.Mockito.when(app.getBuyerId()).thenReturn(buyerId);
        org.mockito.Mockito.when(app.getSellerId()).thenReturn(sellerId);
        org.mockito.Mockito.when(escrowApplicationRepository.findById(escrowId))
                .thenReturn(java.util.Optional.of(app));

        assertThat(service.getById(seed.getId(), sellerId).id()).isEqualTo(seed.getId());

        assertThatThrownBy(() -> service.getById(seed.getId(), 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_FORBIDDEN);
    }

    @Test
    @DisplayName("getById 닉네임 포함_요청자/라이더 이름이 응답에 노출")
    void getById_닉네임_포함() {
        DeliveryResult created = service.create(newCommand());
        service.accept(created.id(), RIDER);

        Map<Long, UserApplicationService.UserProjection> projections = Map.of(
                REQUESTER, new UserApplicationService.UserProjection(REQUESTER, "요청자홍길동", null),
                RIDER, new UserApplicationService.UserProjection(RIDER, "더미 라이더", null)
        );
        org.mockito.Mockito.when(userService.findProjectionsByIds(any())).thenReturn(projections);

        DeliveryResult result = service.getById(created.id(), REQUESTER);

        assertThat(result.requesterNickname()).isEqualTo("요청자홍길동");
        assertThat(result.riderNickname()).isEqualTo("더미 라이더");
    }

    @Test
    @DisplayName("adminGetStats_status 별 카운트 + 정산완료 fee 합계 (follow-up #52)")
    void adminGetStats_집계() {
        // 시드: 모집중 2, 수락 1, 정산완료 2 (fee 5000+7000=12000), 취소 1
        DeliveryResult c1 = service.create(newCommandWithFee(5000L));
        service.create(newCommandWithFee(3000L));  // 모집중 유지
        DeliveryResult c3 = service.create(newCommandWithFee(4000L));
        service.accept(c3.id(), RIDER);  // 수락
        DeliveryResult c4 = service.create(newCommandWithFee(5000L));
        service.accept(c4.id(), RIDER);
        markSettled(c4.id());  // 정산완료 5000
        DeliveryResult c5 = service.create(newCommandWithFee(7000L));
        service.accept(c5.id(), RIDER);
        markSettled(c5.id());  // 정산완료 7000
        service.cancel(c1.id(), REQUESTER, "변심");  // 취소

        com.sseulang.domain.delivery.application.dto.DeliveryStatsResult stats = service.adminGetStats();

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.byStatus()).containsEntry(DeliveryStatus.모집중, 1L);
        assertThat(stats.byStatus()).containsEntry(DeliveryStatus.수락, 1L);
        assertThat(stats.byStatus()).containsEntry(DeliveryStatus.정산완료, 2L);
        assertThat(stats.byStatus()).containsEntry(DeliveryStatus.취소, 1L);
        assertThat(stats.byStatus()).containsEntry(DeliveryStatus.배송중, 0L);  // enum 전부 채움
        assertThat(stats.byStatus()).containsEntry(DeliveryStatus.배송완료, 0L);
        assertThat(stats.settledFeeTotal()).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("adminGetStats 0건_total 0 + 모든 status 0L")
    void adminGetStats_빈상태() {
        com.sseulang.domain.delivery.application.dto.DeliveryStatsResult stats = service.adminGetStats();

        assertThat(stats.total()).isZero();
        assertThat(stats.settledFeeTotal()).isZero();
        for (DeliveryStatus s : DeliveryStatus.values()) {
            assertThat(stats.byStatus()).containsEntry(s, 0L);
        }
    }

    private void markSettled(Long deliveryId) {
        // 배송중 → 배송완료 → 정산완료 전이 (단위 테스트 — pointService 는 mock 이라 transferForDelivery noop)
        service.markPickedUp(deliveryId, RIDER);
        service.markDelivered(deliveryId, RIDER);
        service.complete(deliveryId, REQUESTER);
    }

    private static DeliveryCreateCommand newCommand() {
        return newCommandWithFee(5000L);
    }

    private static DeliveryCreateCommand newCommandWithFee(long fee) {
        return new DeliveryCreateCommand(
                REQUESTER,
                "서울 강남구 테헤란로 123",
                "서울 송파구 올림픽로 456",
                "A4 서류 봉투 1개",
                fee,
                LocalDateTime.now().plusHours(2),
                "1층 로비 보관함"
        );
    }
}
