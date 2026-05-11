package com.sseulang.domain.escrow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateCommand;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateInternalCommand;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewCommand;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewResult;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationResult;
import com.sseulang.domain.escrow.application.dto.EscrowLinkCreateCommand;
import com.sseulang.domain.escrow.application.dto.EscrowLinkResult;
import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.domain.escrow.domain.EscrowFeeCalculator;
import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import com.sseulang.domain.escrow.domain.EscrowFeeSettingsRepository;
import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.EscrowLinkRepository;
import com.sseulang.domain.escrow.domain.EscrowLinkStatus;
import com.sseulang.domain.escrow.domain.FeeBreakdown;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.escrow.domain.event.EscrowConfirmedEvent;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EscrowApplicationService {

    private static final ObjectMapper IMAGE_JSON = new ObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final EscrowLinkRepository linkRepository;
    private final EscrowApplicationRepository applicationRepository;
    private final EscrowFeeSettingsRepository feeSettingsRepository;
    private final UserApplicationService userApplicationService;
    private final PointApplicationService pointApplicationService;
    private final DeliveryRepository deliveryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.sseulang.domain.chat.application.ChatRoomApplicationService chatRoomApplicationService;
    private final com.sseulang.domain.item.application.ItemApplicationService itemApplicationService;
    private final int linkExpiryHours;

    public EscrowApplicationService(
            EscrowLinkRepository linkRepository,
            EscrowApplicationRepository applicationRepository,
            EscrowFeeSettingsRepository feeSettingsRepository,
            UserApplicationService userApplicationService,
            PointApplicationService pointApplicationService,
            DeliveryRepository deliveryRepository,
            ApplicationEventPublisher eventPublisher,
            com.sseulang.domain.chat.application.ChatRoomApplicationService chatRoomApplicationService,
            com.sseulang.domain.item.application.ItemApplicationService itemApplicationService,
            @Value("${app.escrow.link.expiry-hours:24}") int linkExpiryHours
    ) {
        this.linkRepository = linkRepository;
        this.applicationRepository = applicationRepository;
        this.feeSettingsRepository = feeSettingsRepository;
        this.userApplicationService = userApplicationService;
        this.pointApplicationService = pointApplicationService;
        this.deliveryRepository = deliveryRepository;
        this.eventPublisher = eventPublisher;
        this.chatRoomApplicationService = chatRoomApplicationService;
        this.itemApplicationService = itemApplicationService;
        this.linkExpiryHours = linkExpiryHours;
    }

    
    
    
    
    
    public EscrowApplicationPreviewResult previewFee(EscrowApplicationPreviewCommand cmd) {
        if (cmd.tradeMode() == null || cmd.feePayer() == null
                || cmd.weight() == null || cmd.volume() == null || cmd.fragility() == null
                || cmd.pickupLat() == null || cmd.pickupLng() == null
                || cmd.deliveryLat() == null || cmd.deliveryLng() == null) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        EscrowFeeSettings settings = feeSettingsRepository.findSingleton();
        BigDecimal distance = EscrowFeeCalculator.distanceKm(
                cmd.pickupLat().doubleValue(), cmd.pickupLng().doubleValue(),
                cmd.deliveryLat().doubleValue(), cmd.deliveryLng().doubleValue()
        );
        FeeBreakdown fee = EscrowFeeCalculator.calculate(
                settings, cmd.tradeMode(), cmd.itemPrice(), distance,
                cmd.weight(), cmd.volume(), cmd.fragility()
        );
        long buyerPayable = computeBuyerOwed(cmd.tradeMode(), cmd.itemPrice(), fee, cmd.feePayer());
        long sellerPayable = computeSellerOwed(fee, cmd.feePayer());
        return new EscrowApplicationPreviewResult(
                fee.distanceKm(),
                fee.deliveryFee(),
                fee.commissionFee(),
                fee.totalFee(),
                buyerPayable,
                sellerPayable,
                fee.commissionRate()
        );
    }

    
    
    
    
    
    @Transactional
    public EscrowApplicationResult createInternalApplication(EscrowApplicationCreateInternalCommand cmd) {
        userApplicationService.requireVerified(cmd.requesterId());

        
        com.sseulang.domain.chat.application.ChatRoomApplicationService.ChatRoomMeta meta =
                chatRoomApplicationService.findMetaForParticipant(cmd.chatRoomId(), cmd.requesterId());
        if (!meta.itemId().equals(cmd.itemId())) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (meta.iLeft() || meta.opponentLeft()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_OPPONENT_LEFT);
        }

        
        var itemInfo = itemApplicationService.findActiveForTransaction(cmd.itemId());
        if (!itemInfo.sellerId().equals(cmd.requesterId())) {
            throw new BusinessException(ErrorCode.ESCROW_SELLER_ONLY);
        }

        
        Long buyerId = chatRoomApplicationService.findOpponent(cmd.chatRoomId(), cmd.requesterId());

        
        EscrowFeeSettings settings = feeSettingsRepository.findSingleton();
        BigDecimal calculatedDistance = EscrowFeeCalculator.distanceKm(
                cmd.pickupLat().doubleValue(), cmd.pickupLng().doubleValue(),
                cmd.deliveryLat().doubleValue(), cmd.deliveryLng().doubleValue()
        );
        FeeBreakdown calculated = EscrowFeeCalculator.calculate(
                settings, cmd.tradeMode(), cmd.itemPrice(), calculatedDistance,
                cmd.weight(), cmd.volume(), cmd.fragility()
        );
        EscrowFeeCalculator.verifyTolerance(
                calculated, cmd.submittedDeliveryFee(), cmd.submittedCommissionFee(), cmd.submittedTotalFee()
        );

        
        long buyerOwed = computeBuyerOwed(cmd.tradeMode(), cmd.itemPrice(), calculated, cmd.feePayer());
        long sellerOwed = computeSellerOwed(calculated, cmd.feePayer());
        long initiatorShare = sellerOwed;
        long receiverShare = buyerOwed;

        EscrowApplication app = EscrowApplication.createInternal(
                cmd.chatRoomId(),
                cmd.requesterId(),  
                buyerId,            
                cmd.tradeMode(), cmd.feePayer(),
                cmd.itemPrice(), cmd.itemDescription(),
                cmd.pickupAddress(), cmd.pickupLat(), cmd.pickupLng(),
                cmd.deliveryAddress(), cmd.deliveryLat(), cmd.deliveryLng(),
                cmd.weight(), cmd.volume(), cmd.fragility(), cmd.deliveryNotes(),
                calculated, initiatorShare, receiverShare,
                serializeImageUrls(cmd.imageUrls())
        );
        EscrowApplication saved = applicationRepository.save(app);
        return EscrowApplicationResult.from(saved, parseImageUrls(saved.getImageUrls()));
    }

    
    
    
    
    @Transactional
    public EscrowApplicationResult createInternalDraft(
            com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateInternalDraftCommand cmd
    ) {
        userApplicationService.requireVerified(cmd.requesterId());

        com.sseulang.domain.chat.application.ChatRoomApplicationService.ChatRoomMeta meta =
                chatRoomApplicationService.findMetaForParticipant(cmd.chatRoomId(), cmd.requesterId());
        if (!meta.itemId().equals(cmd.itemId())) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (meta.iLeft() || meta.opponentLeft()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_OPPONENT_LEFT);
        }

        
        var itemInfo = itemApplicationService.findActiveForTransaction(cmd.itemId());
        if (!itemInfo.sellerId().equals(cmd.requesterId())) {
            throw new BusinessException(ErrorCode.ESCROW_SELLER_ONLY);
        }

        Long buyerId = chatRoomApplicationService.findOpponent(cmd.chatRoomId(), cmd.requesterId());

        EscrowApplication app = EscrowApplication.createInternalDraft(
                cmd.chatRoomId(),
                cmd.requesterId(), buyerId,
                cmd.tradeMode(), cmd.feePayer(),
                cmd.itemPrice(), cmd.itemDescription(),
                cmd.pickupAddress(), cmd.pickupLat(), cmd.pickupLng(),
                cmd.weight(), cmd.volume(), cmd.fragility(), cmd.deliveryNotes(),
                serializeImageUrls(cmd.imageUrls())
        );
        EscrowApplication saved = applicationRepository.save(app);
        return EscrowApplicationResult.from(saved, parseImageUrls(saved.getImageUrls()));
    }

    
    
    
    @Transactional
    public EscrowApplicationResult patchSellerInfo(
            Long applicationId,
            Long requesterId,
            com.sseulang.domain.escrow.application.dto.EscrowSellerInfoPatchCommand cmd
    ) {
        EscrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (!app.getSellerId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        app.patchSellerInfo(
                cmd.pickupAddress(), cmd.pickupLat(), cmd.pickupLng(),
                cmd.weight(), cmd.volume(), cmd.fragility(),
                cmd.itemPrice(), cmd.itemDescription(), cmd.deliveryNotes()
        );
        return EscrowApplicationResult.from(app, parseImageUrls(app.getImageUrls()));
    }

    
    
    
    @Transactional
    public EscrowApplicationResult patchBuyerInfo(
            Long applicationId,
            Long requesterId,
            com.sseulang.domain.escrow.application.dto.EscrowBuyerInfoPatchCommand cmd
    ) {
        userApplicationService.requireVerified(requesterId);
        EscrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (!app.getBuyerId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        app.patchBuyerInfo(cmd.deliveryAddress(), cmd.deliveryLat(), cmd.deliveryLng(), cmd.receiverPhone());

        
        if (app.isSellerInfoFilled() && app.isBuyerInfoFilled()) {
            EscrowFeeSettings settings = feeSettingsRepository.findSingleton();
            BigDecimal distance = EscrowFeeCalculator.distanceKm(
                    app.getPickupLat().doubleValue(), app.getPickupLng().doubleValue(),
                    app.getDeliveryLat().doubleValue(), app.getDeliveryLng().doubleValue()
            );
            FeeBreakdown calculated = EscrowFeeCalculator.calculate(
                    settings, app.getTradeMode(), app.getItemPrice(), distance,
                    app.getWeight(), app.getVolume(), app.getFragility()
            );
            long buyerOwed = computeBuyerOwed(app.getTradeMode(), app.getItemPrice(), calculated, app.getFeePayer());
            long sellerOwed = computeSellerOwed(calculated, app.getFeePayer());
            long initiatorShare = sellerOwed;  
            long receiverShare = buyerOwed;    
            app.transitionToReadyForPayment(calculated, initiatorShare, receiverShare);
        }

        return EscrowApplicationResult.from(app, parseImageUrls(app.getImageUrls()));
    }

    
    
    
    @Transactional
    public EscrowLinkResult createLink(EscrowLinkCreateCommand cmd) {
        userApplicationService.requireVerified(cmd.initiatorId());
        EscrowLink link = EscrowLink.create(
                cmd.initiatorId(),
                cmd.initiatorRole(),
                cmd.feePayer(),
                cmd.tradeMode(),
                linkExpiryHours
        );
        EscrowLink saved = linkRepository.save(link);
        User initiator = userApplicationService.getById(cmd.initiatorId());
        return EscrowLinkResult.from(saved, initiator.getNickname());
    }

    
    
    
    public EscrowLinkResult getByToken(String linkToken) {
        EscrowLink link = linkRepository.findByLinkToken(linkToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_LINK_NOT_FOUND));
        if (link.isExpired() && link.getStatus() == EscrowLinkStatus.대기) {
            throw new BusinessException(ErrorCode.ESCROW_LINK_EXPIRED);
        }
        if (link.getStatus() == EscrowLinkStatus.만료) {
            throw new BusinessException(ErrorCode.ESCROW_LINK_EXPIRED);
        }
        if (link.getStatus() == EscrowLinkStatus.완료 || link.getStatus() == EscrowLinkStatus.취소) {
            throw new BusinessException(ErrorCode.ESCROW_LINK_ALREADY_TAKEN);
        }
        User initiator = userApplicationService.getById(link.getInitiatorId());
        return EscrowLinkResult.from(link, initiator.getNickname());
    }

    
    
    
    
    @Transactional
    public EscrowApplicationResult createApplication(EscrowApplicationCreateCommand cmd) {
        userApplicationService.requireVerified(cmd.receiverId());

        EscrowLink link = linkRepository.findByLinkToken(cmd.linkToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_LINK_NOT_FOUND));

        
        if (cmd.receiverId().equals(link.getReceiverId())) {
            return applicationRepository.findByLinkId(link.getId())
                    .map(a -> EscrowApplicationResult.from(a, parseImageUrls(a.getImageUrls())))
                    .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_INVALID_STATE));
        }

        
        int affected = linkRepository.claimReceiverIfAvailable(link.getId(), cmd.receiverId());
        if (affected == 0) {
            
            if (link.getInitiatorId().equals(cmd.receiverId())) {
                throw new BusinessException(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
            }
            if (link.isExpired() || link.getStatus() == EscrowLinkStatus.만료) {
                throw new BusinessException(ErrorCode.ESCROW_LINK_EXPIRED);
            }
            throw new BusinessException(ErrorCode.ESCROW_LINK_ALREADY_TAKEN);
        }

        
        EscrowFeeSettings settings = feeSettingsRepository.findSingleton();
        BigDecimal calculatedDistance = EscrowFeeCalculator.distanceKm(
                cmd.pickupLat().doubleValue(), cmd.pickupLng().doubleValue(),
                cmd.deliveryLat().doubleValue(), cmd.deliveryLng().doubleValue()
        );
        FeeBreakdown calculated = EscrowFeeCalculator.calculate(
                settings, link.getTradeMode(), cmd.itemPrice(), calculatedDistance,
                cmd.weight(), cmd.volume(), cmd.fragility()
        );
        
        EscrowFeeCalculator.verifyTolerance(
                calculated, cmd.submittedDeliveryFee(), cmd.submittedCommissionFee(), cmd.submittedTotalFee()
        );

        
        long buyerOwed = computeBuyerOwed(link.getTradeMode(), cmd.itemPrice(), calculated, link.getFeePayer());
        long sellerOwed = computeSellerOwed(calculated, link.getFeePayer());
        long initiatorShare = link.getInitiatorRole() == InitiatorRole.buyer ? buyerOwed : sellerOwed;
        long receiverShare = link.getInitiatorRole() == InitiatorRole.buyer ? sellerOwed : buyerOwed;

        EscrowApplication app = EscrowApplication.create(
                link.getId(),
                link.getInitiatorId(), cmd.receiverId(), link.getInitiatorRole(),
                link.getTradeMode(), link.getFeePayer(),
                cmd.itemPrice(), cmd.itemDescription(),
                cmd.pickupAddress(), cmd.pickupLat(), cmd.pickupLng(),
                cmd.deliveryAddress(), cmd.deliveryLat(), cmd.deliveryLng(),
                cmd.weight(), cmd.volume(), cmd.fragility(), cmd.deliveryNotes(),
                calculated, initiatorShare, receiverShare,
                serializeImageUrls(cmd.imageUrls())
        );
        EscrowApplication saved = applicationRepository.save(app);
        
        link.markAsCompleted();
        return EscrowApplicationResult.from(saved, parseImageUrls(saved.getImageUrls()));
    }

    

    private long computeBuyerOwed(TradeMode mode, long itemPrice, FeeBreakdown fee, FeePayer payer) {
        long feeTotal = fee.deliveryFee() + fee.commissionFee();
        long buyerFeeShare = switch (payer) {
            case buyer -> feeTotal;
            case seller -> 0L;
            case both -> feeTotal / 2;  
        };
        return (mode == TradeMode.INTERNAL ? itemPrice : 0L) + buyerFeeShare;
    }

    private long computeSellerOwed(FeeBreakdown fee, FeePayer payer) {
        long feeTotal = fee.deliveryFee() + fee.commissionFee();
        return switch (payer) {
            case buyer -> 0L;
            case seller -> feeTotal;
            case both -> feeTotal - feeTotal / 2;  
        };
    }

    

    public void verifyChargeIntent(Long applicationId, Long payerId, long amount) {
        EscrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (app.getStatus() != EscrowApplicationStatus.결제대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (app.isPaymentTimedOut()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        long expectedShare;
        if (payerId.equals(app.getInitiatorId())) {
            if (app.getInitiatorPaidAt() != null) {
                throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
            }
            expectedShare = app.getInitiatorShare();
        } else if (payerId.equals(app.getReceiverId())) {
            if (app.getReceiverPaidAt() != null) {
                throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
            }
            expectedShare = app.getReceiverShare();
        } else {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (expectedShare <= 0) {
            
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (expectedShare != amount) {
            throw new BusinessException(ErrorCode.ESCROW_FEE_MISMATCH);
        }
    }

    
    
    
    
    
    @Transactional
    public EscrowApplicationStatus payShare(Long applicationId, Long payerId) {
        userApplicationService.requireVerified(payerId);

        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));

        if (app.getStatus() != EscrowApplicationStatus.결제대기) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (app.isPaymentTimedOut()) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }

        long expectedShare;
        if (payerId.equals(app.getInitiatorId())) {
            if (app.getInitiatorPaidAt() != null) {
                throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
            }
            expectedShare = app.getInitiatorShare() == null ? 0L : app.getInitiatorShare();
        } else if (payerId.equals(app.getReceiverId())) {
            if (app.getReceiverPaidAt() != null) {
                throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
            }
            expectedShare = app.getReceiverShare() == null ? 0L : app.getReceiverShare();
        } else {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }

        if (expectedShare <= 0) {
            
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }

        
        pointApplicationService.deduct(
                payerId, expectedShare,
                com.sseulang.domain.point.domain.PointHistoryType.결제,
                com.sseulang.domain.point.domain.PointReferenceType.ESCROW,
                app.getId(),
                "거래대행 결제 — 본인 분담분"
        );

        
        if (payerId.equals(app.getInitiatorId())) {
            app.markInitiatorPaid();
        } else {
            app.markReceiverPaid();
        }

        
        if (app.getStatus() == EscrowApplicationStatus.결제완료) {
            eventPublisher.publishEvent(new EscrowConfirmedEvent(
                    app.getId(),
                    app.getPickupAddress(), app.getPickupLat().doubleValue(), app.getPickupLng().doubleValue(),
                    app.getDeliveryAddress(), app.getDeliveryLat().doubleValue(), app.getDeliveryLng().doubleValue(),
                    app.getItemDescription(),
                    app.getAppliedDeliveryFee(),
                    app.getBuyerId()
            ));
        }
        return app.getStatus();
    }

    
    
    
    @Transactional
    public EscrowApplicationStatus recordPaymentConfirmed(Long applicationId, Long payerId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (app.isPaymentTimedOut()) {
            
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (payerId.equals(app.getInitiatorId())) {
            app.markInitiatorPaid();
        } else if (payerId.equals(app.getReceiverId())) {
            app.markReceiverPaid();
        } else {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        
        if (app.getStatus() == EscrowApplicationStatus.결제완료) {
            eventPublisher.publishEvent(new EscrowConfirmedEvent(
                    app.getId(),
                    app.getPickupAddress(), app.getPickupLat().doubleValue(), app.getPickupLng().doubleValue(),
                    app.getDeliveryAddress(), app.getDeliveryLat().doubleValue(), app.getDeliveryLng().doubleValue(),
                    app.getItemDescription(),
                    app.getAppliedDeliveryFee(),
                    app.getBuyerId()
            ));
        }
        return app.getStatus();
    }

    
    
    
    @Transactional
    public void markInProgress(Long applicationId) {
        EscrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        app.markInProgress();
    }

    
    
    
    
    @Transactional
    public void confirmReceipt(Long applicationId, Long requesterId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        app.confirmReceipt(requesterId);
        
        Long riderId = deliveryRepository.findByEscrowApplicationId(applicationId)
                .map(d -> d.getRiderId())
                .orElse(null);
        if (riderId == null) {
            
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        settle(app, riderId);
    }

    

    @Transactional
    public void settleAfterDelivery(Long applicationId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (app.getTradeMode() == TradeMode.INTERNAL) {
            
            return;
        }
        Long riderId = deliveryRepository.findByEscrowApplicationId(applicationId)
                .map(d -> d.getRiderId())
                .orElse(null);
        if (riderId == null) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        settle(app, riderId);
    }

    

    private void settle(EscrowApplication app, Long riderId) {
        if (app.getTradeMode() == TradeMode.INTERNAL && app.getItemPrice() > 0) {
            pointApplicationService.credit(
                    app.getSellerId(), app.getItemPrice(),
                    PointHistoryType.판매정산, PointReferenceType.ESCROW, app.getId(),
                    "거래대행 정산 — 판매자 수령"
            );
        }
        if (riderId != null && app.getAppliedDeliveryFee() > 0) {
            pointApplicationService.credit(
                    riderId, app.getAppliedDeliveryFee(),
                    PointHistoryType.배달정산, PointReferenceType.ESCROW, app.getId(),
                    "거래대행 정산 — 라이더 보상"
            );
        }
        app.markSettled();
    }

    
    
    
    @Transactional
    public void cancel(Long applicationId, Long requesterId, String reason) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (!app.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (app.getStatus().isAfterMatching()) {
            
            
            
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        
        
        app.cancel(requesterId, reason);
    }

    
    
    
    public Page<EscrowApplicationResult> listMine(Long userId, Pageable pageable) {
        return applicationRepository.findMyApplications(userId, pageable)
                .map(a -> EscrowApplicationResult.from(a, parseImageUrls(a.getImageUrls())));
    }

    public EscrowApplicationResult getById(Long id, Long requesterId) {
        EscrowApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (!app.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        return EscrowApplicationResult.from(app, parseImageUrls(app.getImageUrls()));
    }

    
    
    
    public Page<EscrowApplicationResult> adminListAll(Pageable pageable) {
        return applicationRepository.findAll(pageable)
                .map(a -> EscrowApplicationResult.from(a, parseImageUrls(a.getImageUrls())));
    }

    public EscrowApplicationResult adminGetById(Long id) {
        EscrowApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        return EscrowApplicationResult.from(app, parseImageUrls(app.getImageUrls()));
    }

    
    private String serializeImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        try {
            return IMAGE_JSON.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("imageUrls 직렬화 실패", e);
        }
    }

    private List<String> parseImageUrls(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return IMAGE_JSON.readValue(json, IMAGE_JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
