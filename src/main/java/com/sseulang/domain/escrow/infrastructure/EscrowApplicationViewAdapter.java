package com.sseulang.domain.escrow.infrastructure;

import com.sseulang.domain.chat.domain.EscrowApplicationView;
import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EscrowApplicationViewAdapter implements EscrowApplicationView {

    private final EscrowApplicationRepository applicationRepository;
    private final DeliveryRepository deliveryRepository;
    private final TransactionRepository transactionRepository;

    public EscrowApplicationViewAdapter(
            EscrowApplicationRepository applicationRepository,
            DeliveryRepository deliveryRepository,
            TransactionRepository transactionRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.deliveryRepository = deliveryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Map<Long, EscrowApplicationProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return Map.of();
        List<EscrowApplication> apps = applicationRepository.findLatestNonCanceledByChatRoomIdIn(chatRoomIds);
        if (apps.isEmpty()) return Map.of();

        List<Long> appIds = apps.stream().map(EscrowApplication::getId).toList();
        Map<Long, Long> deliveryByApp = new HashMap<>();
        for (DeliveryRequest d : deliveryRepository.findByEscrowApplicationIdIn(appIds)) {
            deliveryByApp.put(d.getEscrowApplicationId(), d.getId());
        }
        // 라운드 12 — escrow 완료 시 paired Transaction 매핑. card 에서 리뷰용 transactionId 노출.
        Map<Long, Long> txByApp = new HashMap<>();
        for (Transaction t : transactionRepository.findByEscrowApplicationIdIn(appIds)) {
            txByApp.put(t.getEscrowApplicationId(), t.getId());
        }

        Map<Long, EscrowApplicationProjection> map = new HashMap<>();
        for (EscrowApplication a : apps) {
            map.put(a.getChatRoomId(),
                    new EscrowApplicationProjection(a.getId(), a.getStatus().name(),
                            deliveryByApp.get(a.getId()), txByApp.get(a.getId()),
                            a.getRentalStartAt(), a.getRentalEndAt()));
        }
        return map;
    }
}
