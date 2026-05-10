package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

/**
 * 내부 거래대행 신청 Command (PR-B-2 라운드 12). 채팅방 안에서 판매자가 한 번에 양쪽 정보 입력.
 *
 * <p>외부 link 흐름과 차이:
 * <ul>
 *   <li>{@code linkToken} 미사용 — chatRoomId 로 채팅방 컨텍스트 검증.</li>
 *   <li>{@code requesterId} 는 판매자 (initiator) — 정책상 sellerOnly 라 chat_room.user1/user2 중 sellerId 와 일치 검증.</li>
 *   <li>buyer 는 chat_room 의 상대방에서 도출 (요청자 명시 X).</li>
 * </ul>
 */
public record EscrowApplicationCreateInternalCommand(
        Long requesterId,            // 판매자 = initiator
        Long chatRoomId,
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
        long submittedDeliveryFee,
        long submittedCommissionFee,
        long submittedTotalFee,
        BigDecimal submittedDistanceKm,
        List<String> imageUrls
) {
}
