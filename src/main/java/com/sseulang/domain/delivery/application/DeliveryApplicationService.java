package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.application.dto.DeliveryCreateCommand;
import com.sseulang.domain.delivery.application.dto.DeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    public DeliveryApplicationService(
            DeliveryRepository deliveryRepository,
            UserApplicationService userApplicationService,
            PointApplicationService pointApplicationService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.userApplicationService = userApplicationService;
        this.pointApplicationService = pointApplicationService;
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
        LocalDateTime now = LocalDateTime.now();
        int affected = deliveryRepository.acceptIfStillOpen(deliveryId, riderId, now);
        if (affected == 0) {
            // race 패배 또는 취소된 요청 — 현재 상태로 메시지 분기
            DeliveryRequest current = findOrThrow(deliveryId);
            if (current.getStatus().canAccept()) {
                // 정상적으론 도달 불가 — DB 일관성 깨진 상태
                throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
            }
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
        return DeliveryResult.from(d);
    }

    /**
     * 요청자 정산 확인 → 포인트 이동 + 상태 전이.
     *
     * <p>요청자 잔액 부족 시 INSUFFICIENT_POINT — 라이더의 선행 적립 (id-asc 순서에 따라 먼저
     * 처리될 수 있음) 도 같은 트랜잭션 안이라 함께 롤백. 상태 전이도 롤백되므로 재시도 가능.</p>
     */
    @Transactional
    public DeliveryResult complete(Long deliveryId, Long requesterId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRequester(requesterId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        if (!d.getStatus().canSettle()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        // 잔액 변동 먼저, 상태 전이 마지막 — 정산 실패 시 상태 전이도 롤백 (대칭).
        pointApplicationService.transferForDelivery(
                d.getRequesterId(),
                d.getRiderId(),
                d.getFee(),
                d.getId(),
                "배달 정산: delivery#" + d.getId()
        );
        d.markSettled(LocalDateTime.now());
        return DeliveryResult.from(d);
    }

    /** 요청자 취소 — 모집중 한정. 잔액 이동 없음 (등록 시 escrow 안 했음). */
    @Transactional
    public DeliveryResult cancel(Long deliveryId, Long requesterId, String reason) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRequester(requesterId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        d.cancelByRequester(LocalDateTime.now(), reason);
        return DeliveryResult.from(d);
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

    public Page<DeliveryResult> listMine(Long userId, Pageable pageable) {
        return deliveryRepository.findByParticipant(userId, pageable).map(DeliveryResult::from);
    }

    private DeliveryRequest findOrThrow(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }
}
