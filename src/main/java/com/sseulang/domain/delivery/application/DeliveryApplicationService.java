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

@Service
@Transactional(readOnly = true)
public class DeliveryApplicationService {

    private final DeliveryRepository deliveryRepository;
    private final UserApplicationService userApplicationService;
    private final PointApplicationService pointApplicationService;
    private final DeliveryLocationCache locationCache;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.sseulang.domain.escrow.domain.EscrowApplicationRepository escrowApplicationRepository;

    public DeliveryApplicationService(
            DeliveryRepository deliveryRepository,
            UserApplicationService userApplicationService,
            PointApplicationService pointApplicationService,
            DeliveryLocationCache locationCache,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            com.sseulang.domain.escrow.domain.EscrowApplicationRepository escrowApplicationRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.userApplicationService = userApplicationService;
        this.pointApplicationService = pointApplicationService;
        this.locationCache = locationCache;
        this.eventPublisher = eventPublisher;
        this.escrowApplicationRepository = escrowApplicationRepository;
    }

    

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

    

    @Transactional
    public DeliveryResult accept(Long deliveryId, Long riderId) {
        userApplicationService.requireVerified(riderId);
        DeliveryRequest d = findOrThrow(deliveryId);
        if (d.isRequester(riderId)) {
            throw new BusinessException(ErrorCode.DELIVERY_SELF_NOT_ALLOWED);
        }
        
        
        if (d.getStatus() != com.sseulang.domain.delivery.domain.DeliveryStatus.모집중) {
            if (d.getStatus() == com.sseulang.domain.delivery.domain.DeliveryStatus.수락) {
                throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
            }
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = deliveryRepository.acceptIfStillOpen(deliveryId, riderId, now);
        if (affected == 0) {
            
            
            
            findOrThrow(deliveryId);
            throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
        }
        return DeliveryResult.from(findOrThrow(deliveryId));
    }

    
    @Transactional
    public DeliveryResult markPickedUp(Long deliveryId, Long riderId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(riderId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        d.markPickedUp(LocalDateTime.now());
        return DeliveryResult.from(d);
    }

    
    @Transactional
    public DeliveryResult markDelivered(Long deliveryId, Long riderId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(riderId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        d.markDelivered(LocalDateTime.now());
        
        eventPublisher.publishEvent(new com.sseulang.domain.delivery.domain.event.DeliveryDeliveredEvent(
                d.getId(), d.getEscrowApplicationId()
        ));
        return DeliveryResult.from(d);
    }

    

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
        
        locationCache.evict(deliveryId);
        return DeliveryResult.from(d);
    }

    

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
            
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
        
        locationCache.evict(deliveryId);
        return DeliveryResult.from(findOrThrow(deliveryId));
    }

    public DeliveryResult getById(Long deliveryId, Long requesterId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (d.getStatus().canAccept() || isViewableByEscrowParticipant(d, requesterId)) {
            return DeliveryResult.from(d);
        }
        throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
    }

    // delivery 직접 참여자(requester/rider) 또는 연결 escrow 의 buyer/seller 인지 확인.
    private boolean isViewableByEscrowParticipant(DeliveryRequest d, Long userId) {
        if (d.isParticipant(userId)) return true;
        if (d.getEscrowApplicationId() == null || userId == null) return false;
        return escrowApplicationRepository.findById(d.getEscrowApplicationId())
                .map(a -> userId.equals(a.getBuyerId()) || userId.equals(a.getSellerId()))
                .orElse(false);
    }

    public Page<DeliveryResult> listOpen(Pageable pageable) {
        return deliveryRepository.findOpenList(pageable).map(DeliveryResult::from);
    }

    

    public void requireParticipant(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!isViewableByEscrowParticipant(d, userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
    }

    

    public void requireRider(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
    }

    

    public void requireRiderTrackable(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!d.isRider(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_FORBIDDEN);
        }
        if (!d.getStatus().canTrackLocation()) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATE);
        }
    }

    
    public void requireParticipantTrackable(Long deliveryId, Long userId) {
        DeliveryRequest d = findOrThrow(deliveryId);
        if (!isViewableByEscrowParticipant(d, userId)) {
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

    

    // 라운드 12 — admin delivery 검색.
    public org.springframework.data.domain.Page<com.sseulang.domain.delivery.application.dto.AdminDeliveryResult> adminSearch(
            DeliveryStatus status, Long riderId, Long requesterId,
            java.time.LocalDateTime createdAfter, java.time.LocalDateTime createdBefore,
            String sort, Pageable pageable
    ) {
        Page<DeliveryRequest> page = deliveryRepository.adminSearch(
                status, riderId, requesterId, createdAfter, createdBefore, sort, pageable);
        if (page.isEmpty()) return org.springframework.data.domain.Page.empty(pageable);
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (DeliveryRequest d : page.getContent()) {
            userIds.add(d.getRequesterId());
            if (d.getRiderId() != null) userIds.add(d.getRiderId());
        }
        java.util.Map<Long, UserApplicationService.UserProjection> userMap =
                userApplicationService.findProjectionsByIds(userIds);
        return page.map(d -> com.sseulang.domain.delivery.application.dto.AdminDeliveryResult.from(
                d,
                java.util.Optional.ofNullable(userMap.get(d.getRequesterId()))
                        .map(UserApplicationService.UserProjection::nickname).orElse(null),
                d.getRiderId() == null ? null : java.util.Optional.ofNullable(userMap.get(d.getRiderId()))
                        .map(UserApplicationService.UserProjection::nickname).orElse(null)
        ));
    }

    public com.sseulang.domain.delivery.application.dto.AdminDeliveryStatsResult adminGetStatsV2() {
        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus s : DeliveryStatus.values()) byStatus.put(s, 0L);
        long total = 0;
        for (DeliveryStatusCount row : deliveryRepository.countGroupByStatus()) {
            byStatus.put(row.status(), row.count());
            total += row.count();
        }
        long todayNew = deliveryRepository.countCreatedSince(
                java.time.LocalDate.now().atStartOfDay());
        return new com.sseulang.domain.delivery.application.dto.AdminDeliveryStatsResult(byStatus, total, todayNew);
    }

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
