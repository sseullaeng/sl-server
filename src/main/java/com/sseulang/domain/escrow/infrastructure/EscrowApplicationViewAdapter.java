package com.sseulang.domain.escrow.infrastructure;

import com.sseulang.domain.chat.domain.EscrowApplicationView;
import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EscrowApplicationViewAdapter implements EscrowApplicationView {

    private final EscrowApplicationRepository applicationRepository;
    private final DeliveryRepository deliveryRepository;

    public EscrowApplicationViewAdapter(
            EscrowApplicationRepository applicationRepository,
            DeliveryRepository deliveryRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.deliveryRepository = deliveryRepository;
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

        Map<Long, EscrowApplicationProjection> map = new HashMap<>();
        for (EscrowApplication a : apps) {
            map.put(a.getChatRoomId(),
                    new EscrowApplicationProjection(a.getId(), a.getStatus().name(), deliveryByApp.get(a.getId())));
        }
        return map;
    }
}
