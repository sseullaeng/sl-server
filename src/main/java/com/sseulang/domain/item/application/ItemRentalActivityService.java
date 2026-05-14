package com.sseulang.domain.item.application;

import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ItemRentalActivityService {

    private final TransactionRepository transactionRepository;
    private final EscrowApplicationRepository escrowApplicationRepository;

    public ItemRentalActivityService(
            TransactionRepository transactionRepository,
            // SettlementRollbackIT 같은 sliced context 에선 escrow 패키지 미주입 — null 허용.
            @Autowired(required = false) EscrowApplicationRepository escrowApplicationRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.escrowApplicationRepository = escrowApplicationRepository;
    }

    public Set<Long> findActiveRentalItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> active = new LinkedHashSet<>();
        Set<Long> transactionIds = transactionRepository == null
                ? Set.of()
                : transactionRepository.findActiveRentalItemIds(itemIds);
        if (transactionIds != null && !transactionIds.isEmpty()) {
            active.addAll(transactionIds);
        }
        Set<Long> escrowIds = escrowApplicationRepository == null
                ? Set.of()
                : escrowApplicationRepository.findActiveRentalItemIds(itemIds);
        if (escrowIds != null && !escrowIds.isEmpty()) {
            active.addAll(escrowIds);
        }
        return active;
    }

    public boolean isRentalActive(Long itemId) {
        if (itemId == null) {
            return false;
        }
        return findActiveRentalItemIds(java.util.List.of(itemId)).contains(itemId);
    }
}
