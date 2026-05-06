package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EscrowApplicationResult(
        Long id,
        Long linkId,
        Long initiatorId,
        Long receiverId,
        Long buyerId,
        Long sellerId,
        TradeMode tradeMode,
        FeePayer feePayer,
        long itemPrice,
        String itemDescription,
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        String deliveryNotes,
        BigDecimal appliedDistanceKm,
        long appliedDeliveryFee,
        long appliedCommissionFee,
        long appliedTotalFee,
        BigDecimal appliedCommissionRate,
        long initiatorShare,
        long receiverShare,
        LocalDateTime initiatorPaidAt,
        LocalDateTime receiverPaidAt,
        LocalDateTime paymentDueAt,
        EscrowApplicationStatus status,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime receiptConfirmedAt,
        LocalDateTime settledAt,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EscrowApplicationResult from(EscrowApplication a, List<String> imageUrls) {
        return new EscrowApplicationResult(
                a.getId(), a.getLinkId(),
                a.getInitiatorId(), a.getReceiverId(),
                a.getBuyerId(), a.getSellerId(),
                a.getTradeMode(), a.getFeePayer(),
                a.getItemPrice(), a.getItemDescription(),
                a.getPickupAddress(), a.getPickupLat(), a.getPickupLng(),
                a.getDeliveryAddress(), a.getDeliveryLat(), a.getDeliveryLng(),
                a.getWeight(), a.getVolume(), a.getFragility(), a.getDeliveryNotes(),
                a.getAppliedDistanceKm(), a.getAppliedDeliveryFee(), a.getAppliedCommissionFee(),
                a.getAppliedTotalFee(), a.getAppliedCommissionRate(),
                a.getInitiatorShare(), a.getReceiverShare(),
                a.getInitiatorPaidAt(), a.getReceiverPaidAt(), a.getPaymentDueAt(),
                a.getStatus(), a.getCancelReason(), a.getCancelledBy(),
                a.getReceiptConfirmedAt(), a.getSettledAt(),
                imageUrls,
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
