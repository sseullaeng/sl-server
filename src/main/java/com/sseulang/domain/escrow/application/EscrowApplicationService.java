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

/**
 * 거래대행 (Escrow) 흐름의 트랜잭션 경계. 8 use case + 결제 통합 + 정산 흐름.
 *
 * <p>핵심 보안/정합성 (Codex 게이트 1 영역):
 * <ul>
 *   <li>link 첫 폼 제출 atomic UPDATE (race-safe) — 결정 #2/3</li>
 *   <li>snapshot 보존 — 운영 settings 변경 무관 lock — 결정 #9/12</li>
 *   <li>fee mismatch ±10원 검증 — 결정 #10</li>
 *   <li>매칭 후 취소 100% 부담 정책 — 결정 #6 (cancel 메서드 분기)</li>
 *   <li>자기거래 차단 (initiator != receiver, buyer != seller)</li>
 * </ul>
 */
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

    // =============================================================
    // Use case 0 — 폼 작성 중 수수료 미리보기 (PR-B 라운드 12)
    // 좌표/물품/feePayer 받아서 거리·deliveryFee·commissionFee + buyer/seller 부담분 산정.
    // application 생성 X — pure read.
    // =============================================================
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

    // =============================================================
    // Use case 0b — 내부 신청 (PR-B-3 라운드 12). 채팅방 안에서 판매자가 한 번에 양쪽 정보 입력.
    // 외부 link 흐름 (createApplication) 와 분리.
    // 검증: chatRoom 참여자 + chatRoom.itemId == cmd.itemId + 본인 == item.sellerId.
    // =============================================================
    @Transactional
    public EscrowApplicationResult createInternalApplication(EscrowApplicationCreateInternalCommand cmd) {
        userApplicationService.requireVerified(cmd.requesterId());

        // chatRoom 참여자 검증 + 메타 (itemId)
        com.sseulang.domain.chat.application.ChatRoomApplicationService.ChatRoomMeta meta =
                chatRoomApplicationService.findMetaForParticipant(cmd.chatRoomId(), cmd.requesterId());
        if (!meta.itemId().equals(cmd.itemId())) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (meta.iLeft() || meta.opponentLeft()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_OPPONENT_LEFT);
        }

        // 판매자 검증 — itemId → item.sellerId == requesterId
        var itemInfo = itemApplicationService.findActiveForTransaction(cmd.itemId());
        if (!itemInfo.sellerId().equals(cmd.requesterId())) {
            throw new BusinessException(ErrorCode.ESCROW_SELLER_ONLY);
        }

        // buyer = chatRoom 의 상대방
        Long buyerId = chatRoomApplicationService.findOpponent(cmd.chatRoomId(), cmd.requesterId());

        // fee 산정 (외부 흐름과 동일 방식)
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

        // share 산정. initiator = seller, receiver = buyer (내부 흐름은 seller 가 시작).
        long buyerOwed = computeBuyerOwed(cmd.tradeMode(), cmd.itemPrice(), calculated, cmd.feePayer());
        long sellerOwed = computeSellerOwed(calculated, cmd.feePayer());
        long initiatorShare = sellerOwed;
        long receiverShare = buyerOwed;

        EscrowApplication app = EscrowApplication.createInternal(
                cmd.chatRoomId(),
                cmd.requesterId(),  // initiator = seller
                buyerId,            // receiver = buyer
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

    // =============================================================
    // Use case 0c — 내부 draft (PR-B-4 라운드 12). 판매자가 본인 영역만 입력 → 정보입력대기.
    // 구매자는 patchBuyerInfo 로 본인 영역 추가 입력 → 양쪽 filled 시 결제대기 전환.
    // =============================================================
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

        // 판매자만
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

    // =============================================================
    // Use case 0d — 판매자 영역 수정 (PR-B-4).
    // =============================================================
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

    // =============================================================
    // Use case 0e — 구매자 영역 입력 (PR-B-4). 양쪽 filled 시 결제대기 전환.
    // =============================================================
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

        // 양쪽 filled — fee 산정 + 결제대기 전환
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
            long initiatorShare = sellerOwed;  // initiator = seller (내부 흐름)
            long receiverShare = buyerOwed;    // receiver = buyer
            app.transitionToReadyForPayment(calculated, initiatorShare, receiverShare);
        }

        return EscrowApplicationResult.from(app, parseImageUrls(app.getImageUrls()));
    }

    // =============================================================
    // Use case 1 — 신청자가 link 생성
    // =============================================================
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

    // =============================================================
    // Use case 2 — link 진입 (비로그인 OK, 결정 #1 A1)
    // =============================================================
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

    // =============================================================
    // Use case 3 — 폼 제출 (수신자 확정 + application 생성)
    // 결정 #2 B3 + #3 C1+C3 (atomic + idempotent)
    // =============================================================
    @Transactional
    public EscrowApplicationResult createApplication(EscrowApplicationCreateCommand cmd) {
        userApplicationService.requireVerified(cmd.receiverId());

        EscrowLink link = linkRepository.findByLinkToken(cmd.linkToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_LINK_NOT_FOUND));

        // idempotent — 이미 본인이 receiver 면 기존 application 반환
        if (cmd.receiverId().equals(link.getReceiverId())) {
            return applicationRepository.findByLinkId(link.getId())
                    .map(a -> EscrowApplicationResult.from(a, parseImageUrls(a.getImageUrls())))
                    .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_INVALID_STATE));
        }

        // race lose / 본인 차단 / 만료 일괄 검증 — atomic UPDATE
        int affected = linkRepository.claimReceiverIfAvailable(link.getId(), cmd.receiverId());
        if (affected == 0) {
            // 어떤 사유인지 분기
            if (link.getInitiatorId().equals(cmd.receiverId())) {
                throw new BusinessException(ErrorCode.ESCROW_SELF_NOT_ALLOWED);
            }
            if (link.isExpired() || link.getStatus() == EscrowLinkStatus.만료) {
                throw new BusinessException(ErrorCode.ESCROW_LINK_EXPIRED);
            }
            throw new BusinessException(ErrorCode.ESCROW_LINK_ALREADY_TAKEN);
        }

        // snapshot — fee 산정
        EscrowFeeSettings settings = feeSettingsRepository.findSingleton();
        BigDecimal calculatedDistance = EscrowFeeCalculator.distanceKm(
                cmd.pickupLat().doubleValue(), cmd.pickupLng().doubleValue(),
                cmd.deliveryLat().doubleValue(), cmd.deliveryLng().doubleValue()
        );
        FeeBreakdown calculated = EscrowFeeCalculator.calculate(
                settings, link.getTradeMode(), cmd.itemPrice(), calculatedDistance,
                cmd.weight(), cmd.volume(), cmd.fragility()
        );
        // 결정 #10 — ±10원 tolerance
        EscrowFeeCalculator.verifyTolerance(
                calculated, cmd.submittedDeliveryFee(), cmd.submittedCommissionFee(), cmd.submittedTotalFee()
        );

        // share 산정 — feePayer 별 분기 (결정 #5)
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
        // link 상태 → 완료
        link.markAsCompleted();
        return EscrowApplicationResult.from(saved, parseImageUrls(saved.getImageUrls()));
    }

    /**
     * Mode B 의 buyer 부담 = itemPrice + (feePayer 별 fee 분담).
     * Mode A 의 buyer 부담 = (feePayer 별 fee 분담).
     */
    private long computeBuyerOwed(TradeMode mode, long itemPrice, FeeBreakdown fee, FeePayer payer) {
        long feeTotal = fee.deliveryFee() + fee.commissionFee();
        long buyerFeeShare = switch (payer) {
            case buyer -> feeTotal;
            case seller -> 0L;
            case both -> feeTotal / 2;  // 정수 원 단위 — 홀수 1원 차이는 buyer 가 더 부담
        };
        return (mode == TradeMode.INTERNAL ? itemPrice : 0L) + buyerFeeShare;
    }

    private long computeSellerOwed(FeeBreakdown fee, FeePayer payer) {
        long feeTotal = fee.deliveryFee() + fee.commissionFee();
        return switch (payer) {
            case buyer -> 0L;
            case seller -> feeTotal;
            case both -> feeTotal - feeTotal / 2;  // 나머지 (홀수면 buyer 가 1 더, seller 가 1 적게)
        };
    }

    /**
     * Payment 도메인이 startCharge 단계에서 호출 — payer / share / status 검증.
     * 검증 통과한 amount 만 Payment 진입 (위변조 방지 + race-safe).
     */
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
            // 본인 share 가 0 인데 결제 시도 — 부정 시도.
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (expectedShare != amount) {
            throw new BusinessException(ErrorCode.ESCROW_FEE_MISMATCH);
        }
    }

    // =============================================================
    // Use case 4b — 포인트 잔액 결제 (PR-B-5 라운드 12).
    // 토스 결제창 X — 포인트 잔액에서 본인 share 만큼 차감 + paid 마킹.
    // 양쪽 paid 시 자동 결제완료 + EscrowConfirmedEvent → 라이더 매칭.
    // =============================================================
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
            // 본인 share 가 0 — 결제 의무 없음.
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }

        // 포인트 잔액 차감 (atomic UPDATE — 잔액 부족 시 INSUFFICIENT_POINT throw)
        pointApplicationService.deduct(
                payerId, expectedShare,
                com.sseulang.domain.point.domain.PointHistoryType.결제,
                com.sseulang.domain.point.domain.PointReferenceType.ESCROW,
                app.getId(),
                "거래대행 결제 — 본인 분담분"
        );

        // paid 마킹
        if (payerId.equals(app.getInitiatorId())) {
            app.markInitiatorPaid();
        } else {
            app.markReceiverPaid();
        }

        // 양쪽 결제 완료 시 EscrowConfirmedEvent 발행 → Delivery 도메인 라이더 자동 매칭
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

    // =============================================================
    // Use case 4 — 결제 confirm 후 호출 (Payment 도메인이 호출)
    // =============================================================
    @Transactional
    public EscrowApplicationStatus recordPaymentConfirmed(Long applicationId, Long payerId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (app.isPaymentTimedOut()) {
            // 자동 환불 트리거 — 5/11 시점엔 단순화 — 환불 처리는 Payment 도메인 또는 cron 후속.
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        if (payerId.equals(app.getInitiatorId())) {
            app.markInitiatorPaid();
        } else if (payerId.equals(app.getReceiverId())) {
            app.markReceiverPaid();
        } else {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        // 양쪽 결제 완료 시 EscrowConfirmedEvent 발행 → Delivery 도메인 listen
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

    // =============================================================
    // Use case 5 — 라이더 자동 매칭됐을 때 (Delivery 도메인이 호출)
    // =============================================================
    @Transactional
    public void markInProgress(Long applicationId) {
        EscrowApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        app.markInProgress();
    }

    // =============================================================
    // Use case 6 — buyer 수령 확인 (Mode B 만) → 정산
    // 결정 #4
    // =============================================================
    @Transactional
    public void confirmReceipt(Long applicationId, Long requesterId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        app.confirmReceipt(requesterId);
        // 라이더 식별 — Delivery 도메인에서 escrow_application_id 로 조회 (게이트 1 Critical 2).
        Long riderId = deliveryRepository.findByEscrowApplicationId(applicationId)
                .map(d -> d.getRiderId())
                .orElse(null);
        if (riderId == null) {
            // 라이더 매칭 안 된 상태에서 receipt 호출 — 데이터 부정합 (정산 불가).
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        settle(app, riderId);
    }

    /**
     * 자동 정산 (Mode A — 배송완료 시 Delivery 도메인이 호출).
     */
    @Transactional
    public void settleAfterDelivery(Long applicationId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (app.getTradeMode() == TradeMode.INTERNAL) {
            // Mode B 는 buyer 수령 확인 별도 endpoint 호출
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

    /**
     * 정산 — Mode B: seller += itemPrice, rider += deliveryFee.
     * commissionFee 는 플랫폼 수익 (별도 transfer X — PointHistory 만 기록은 follow-up).
     */
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

    // =============================================================
    // Use case 7 — 취소 (시점별 환불 — 결정 #6)
    // =============================================================
    @Transactional
    public void cancel(Long applicationId, Long requesterId, String reason) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        if (!app.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.ESCROW_FORBIDDEN);
        }
        if (app.getStatus().isAfterMatching()) {
            // 매칭 후 취소 — 100% 부담 정책 (결정 #6).
            // 5/11 단순화: 별도 추가 결제 흐름 미구현 — 관리자 분기 처리.
            // TODO(R1): 자동 추가 결제 + 환불 흐름.
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
        // 매칭 전 — 양쪽 환불 (결제됐던 양만큼).
        // 5/11 단순화: 환불 호출은 Payment 도메인이 별도 처리. 여기선 status만 갱신.
        app.cancel(requesterId, reason);
    }

    // =============================================================
    // Use case 8 — 본인 application 목록 / 단건
    // =============================================================
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

    // =============================================================
    // Admin 우회 — 참여자 가드 X
    // =============================================================
    public Page<EscrowApplicationResult> adminListAll(Pageable pageable) {
        return applicationRepository.findAll(pageable)
                .map(a -> EscrowApplicationResult.from(a, parseImageUrls(a.getImageUrls())));
    }

    public EscrowApplicationResult adminGetById(Long id) {
        EscrowApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        return EscrowApplicationResult.from(app, parseImageUrls(app.getImageUrls()));
    }

    /** 이미지 URL list 직렬화 (TEXT 컬럼). 5/11 단순화 — JSON 배열. */
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
