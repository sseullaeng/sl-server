package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.application.dto.DeliveryCreateCommand;
import com.sseulang.domain.delivery.application.dto.DeliveryResult;
import com.sseulang.domain.delivery.application.dto.DeliveryStatsResult;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;
import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.delivery.domain.DeliveryStatusCount;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Delivery (배달대행) ApplicationService — 트랜잭션 경계 + 도메인 흐름 조율.
 *
 * <p>핵심 흐름:
 * <ol>
 *   <li>{@link #create} — 요청자 등록 (이메일 인증 가드)</li>
 *   <li>{@link #accept} — 라이더 수락 (conditional UPDATE 로 race 차단, 본인 거래 차단)</li>
 *   <li>{@link #markPickedUp} / {@link #markDelivered} — 라이더 상태 전이</li>
 *   <li>{@link #complete} — 요청자가 정산 확인 → 포인트 이동 (요청자 차감 + 라이더 적립)</li>
 *   <li>{@link #cancel} — 요청자 취소 (모집중 한정)</li>
 * </ol>
 *
 * <p>정산({@link #complete})은 {@link PointApplicationService#transferForDelivery} 한 번에
 * 차감/적립 + history 두 건 적재가 한 트랜잭션으로 묶여 정합성 보장.</p>
 *
 * <p>가이드 §3.3 — Repository 인터페이스만 의존 (구현은 infrastructure). 다른 도메인은
 * UserApplicationService / PointApplicationService 만 호출.</p>
 */
@Service
@Transactional(readOnly = true)
public class DeliveryApplicationService {

    private final DeliveryRepository deliveryRepository;
    private final UserApplicationService userApplicationService;
    private final PointApplicationService pointApplicationService;
    private final DeliveryLocationCache locationCache;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public DeliveryApplicationService(
            DeliveryRepository deliveryRepository,
            UserApplicationService userApplicationService,
            PointApplicationService pointApplicationService,
            DeliveryLocationCache locationCache,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.deliveryRepository = deliveryRepository;
        this.userApplicationService = userApplicationService;
        this.pointApplicationService = pointApplicationService;
        this.locationCache = locationCache;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 요청 등록. 요청자 이메일 인증 필수 (가이드 §4 보안 / §10 happy path).
     *
     * <p>현재 정책: <b>등록 시점에 fee escrow 안 함</b> — 정산({@link #complete}) 시점에 차감.
     * 등록 단계 잔액 체크도 안 함 (라이더가 수락 후 요청자 잔액이 부족해지면 정산 실패 →
     * 라이더 항의 가능). 5/6 이전 단순화 정책. follow-up: hold 패턴 도입 고려.</p>
     */
    @Transactional
    public DeliveryResult create(DeliveryCreateCommand cmd) {
        userApplicationService.requireVerified(cmd.requesterId());
        DeliveryRequest d = DeliveryRequest.create(
                cmd.requesterId(),
                cmd.pickupAddress(),
                cmd.dropoffAddress(),
                cmd.itemDescription(),
                cmd.fee(),
                cmd.requestedDeadline(),
                cmd.memo(),
                LocalDateTime.now()
        );
        return DeliveryResult.from(deliveryRepository.save(d));
    }

    /**
     * 라이더 수락. 본인 거래 차단 + conditional UPDATE 로 동시 수락 race 차단.
     *
     * <p>{@link DeliveryRepository#acceptIfStillOpen} 가 {@code WHERE status='모집중'} 조건을
     * 락 없이 atomic 하게 평가 — 영향 행 0 이면 이미 다른 라이더가 수락했거나 취소된 상태.
     * 수락한 라이더는 {@link DeliveryRequest#acceptBy} 호출 결과와 일치 (DB 갱신 = aggregate 갱신).</p>
     */
    @Transactional
    public DeliveryResult accept(Long deliveryId, Long riderId) {
        userApplicationService.requireVerified(riderId);
        DeliveryRequest d = findOrThrow(deliveryId);
        if (d.isRequester(riderId)) {
            throw new BusinessException(ErrorCode.DELIVERY_SELF_NOT_ALLOWED);
        }
        // 사전 조회에서 이미 취소/종료 상태면 ALREADY_ACCEPTED 가 아닌 INVALID_STATE 가 더 정확.
        // (게이트 1 round 2 — Warning) update 이후 race 패배는 아래 분기에서 통일 처리.
        if (d.getStatus() != com.sseulang.domain.delivery.domain.DeliveryStatus.모집중) {
            if (d.getStatus() == com.sseulang.domain.delivery.domain.DeliveryStatus.수락) {
                throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
            }
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = deliveryRepository.acceptIfStillOpen(deliveryId, riderId, now);
        if (affected == 0) {
            // 본인 거래/취소는 위에서 사전 검증으로 차단 — 여기 도달하는 affected=0 은
            // 실제 동시 accept race 패배 케이스. REPEATABLE_READ snapshot 으로 인해 같은 트랜잭션의
            // findById 가 race 우승자 commit 을 못 보는 케이스 회피 위해 ALREADY_ACCEPTED 로 통일.
            findOrThrow(deliveryId);
            throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
        }
        return DeliveryResult.from(findOrThrow(deliveryId));
    }

    /** 라이더 픽업 — 수락한 라이더만. */
    @Transactional
    public DeliveryResult markPickedUp(Long deliveryId, Long riderId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(riderId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        d.markPickedUp(LocalDateTime.now());
        return DeliveryResult.from(d);
    }

    /** 라이더 배송 완료 — 수락한 라이더만. */
    @Transactional
    public DeliveryResult markDelivered(Long deliveryId, Long riderId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(riderId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        d.markDelivered(LocalDateTime.now());
        // Mode A escrow 자동 정산 트리거 — listener (EscrowApplicationService) 가 분기 처리.
        eventPublisher.publishEvent(new com.sseulang.domain.delivery.domain.event.DeliveryDeliveredEvent(
                d.getId(), d.getEscrowApplicationId()
        ));
        return DeliveryResult.from(d);
    }

    /**
     * 요청자 정산 확인 → 포인트 이동 + 상태 전이.
     *
     * <p>게이트 1 round 1 — Critical 1: {@link DeliveryRepository#findByIdForUpdate} 로 row 락을
     * 먼저 획득해 동시 complete 2건이 모두 정산 진입하는 race 차단. 락 획득 후 상태 검증 →
     * {@code transferForDelivery} → {@code markSettled} 순서. 한 트랜잭션 안에서 잔액 변동 실패 시
     * 상태 전이도 함께 롤백.</p>
     *
     * <p>요청자 잔액 부족 시 INSUFFICIENT_POINT — 라이더의 선행 적립 (id-asc 순서에 따라 먼저
     * 처리될 수 있음) 도 같은 트랜잭션 안이라 함께 롤백. 상태 전이도 롤백되므로 재시도 가능.</p>
     */
    @Transactional
    public DeliveryResult complete(Long deliveryId, Long requesterId) {
        DeliveryRequest d = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!d.isRequester(requesterId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        if (!d.getStatus().canSettle()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        pointApplicationService.transferForDelivery(
                d.getRequesterId(),
                d.getRiderId(),
                d.getFee(),
                d.getId(),
                "배달 정산: delivery#" + d.getId()
        );
        d.markSettled(LocalDateTime.now());
        // 위치 캐시 즉시 만료 — 정산완료 후 개인정보 잔류 방지 (Codex 게이트 2 W1).
        locationCache.evict(deliveryId);
        return DeliveryResult.from(d);
    }

    /**
     * 요청자 취소 — 모집중 한정. 잔액 이동 없음 (등록 시 escrow 안 했음).
     *
     * <p>게이트 1 round 1 — Critical 2: {@link DeliveryRepository#cancelIfStillOpen} 조건부
     * UPDATE 로 accept vs cancel race 차단. 영향 행 0 이면 본인 자원 아니거나 이미 수락/취소된 상태.
     * 사유 분기를 위해 후처리 조회로 메시지 결정.</p>
     */
    @Transactional
    public DeliveryResult cancel(Long deliveryId, Long requesterId, String reason) {
        if (reason != null && reason.length() > 255) {
            throw new IllegalArgumentException("cancelReason 은 255자 이하여야 합니다");
        }
        int affected = deliveryRepository.cancelIfStillOpen(
                deliveryId, requesterId, LocalDateTime.now(), reason
        );
        if (affected == 0) {
            DeliveryRequest current = findOrThrow(deliveryId);
            if (!current.isRequester(requesterId)) {
                throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
            }
            // 이미 수락된 / 취소된 / 그 이후 단계 — 모집중 아님.
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        // 모집중에서 취소된 케이스 — 위치 캐시 보통 비어있지만 안전망으로 evict.
        locationCache.evict(deliveryId);
        return DeliveryResult.from(findOrThrow(deliveryId));
    }

    public DeliveryResult getById(Long deliveryId, Long requesterId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        // 모집중은 누구나 조회 가능 (라이더가 수락 결정 위해). 그 외는 참여자만.
        if (!d.getStatus().canAccept() && !d.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        return DeliveryResult.from(d);
    }

    public Page<DeliveryResult> listOpen(Pageable pageable) {
        return deliveryRepository.findOpenList(pageable).map(DeliveryResult::from);
    }

    /**
     * 외부 도메인이 참여자 검증할 때 사용 (예: STOMP location SUBSCRIBE 인가).
     * 참여자 아니면 {@link ErrorCode#DELIVERY_FORBIDDEN}.
     */
    public void requireParticipant(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
    }

    /**
     * 외부 도메인이 라이더 본인 검증할 때 사용 (예: STOMP location publish 권한).
     * 수락된 라이더 아니면 {@link ErrorCode#DELIVERY_FORBIDDEN}.
     */
    public void requireRider(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
    }

    /**
     * 라이더 본인 + 위치 추적 가능 상태({@link DeliveryStatus#canTrackLocation}) 검증.
     * 위치 publish 진입 가드 — 종료(배송완료/정산완료/취소) 후 위치 보낼 수 없게 함 (Codex 게이트 2 W1).
     */
    public void requireRiderTrackable(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        if (!d.getStatus().canTrackLocation()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
    }

    /** 참여자 + 위치 추적 가능 상태 — 종료 후 위치 조회/구독 차단. */
    public void requireParticipantTrackable(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        if (!d.getStatus().canTrackLocation()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
    }

    public Page<DeliveryResult> listMine(Long userId, Pageable pageable) {
        return deliveryRepository.findByParticipant(userId, pageable).map(DeliveryResult::from);
    }

    private DeliveryRequest findOrThrow(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    /**
     * 관리자 dashboard 용 배달대행 통계 (follow-up #52). status 별 카운트 + 정산완료 fee 합계.
     * 모든 status 가 byStatus 에 포함됨 — 0 건이면 0L. 단일 GROUP BY + 단일 SUM 쿼리.
     */
    public DeliveryStatsResult adminGetStats() {
        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus s : DeliveryStatus.values()) {
            byStatus.put(s, 0L);
        }
        long total = 0;
        for (DeliveryStatusCount row : deliveryRepository.countGroupByStatus()) {
            byStatus.put(row.status(), row.count());
            total += row.count();
        }
        long settledFeeTotal = deliveryRepository.sumSettledFee();
        return new DeliveryStatsResult(total, byStatus, settledFeeTotal);
    }
}
