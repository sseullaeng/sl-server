package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.EscrowLinkRepository;
import com.sseulang.domain.escrow.domain.EscrowLinkStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InMemoryFakeEscrowLinkRepository implements EscrowLinkRepository {

    private final Map<Long, EscrowLink> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public EscrowLink save(EscrowLink link) {
        if (link.getId() == null) {
            ReflectionTestUtils.setField(link, "id", ++sequence);
            ReflectionTestUtils.setField(link, "createdAt", LocalDateTime.now());
        }
        ReflectionTestUtils.setField(link, "updatedAt", LocalDateTime.now());
        store.put(link.getId(), link);
        return link;
    }

    @Override
    public Optional<EscrowLink> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<EscrowLink> findByLinkToken(String linkToken) {
        return store.values().stream()
                .filter(l -> l.getLinkToken().equals(linkToken))
                .findFirst();
    }

    @Override
    public Page<EscrowLink> findByInitiatorId(Long initiatorId, Pageable pageable) {
        return new PageImpl<>(store.values().stream()
                .filter(l -> l.getInitiatorId().equals(initiatorId))
                .collect(Collectors.toList()), pageable, store.size());
    }

    @Override
    public int claimReceiverIfAvailable(Long linkId, Long receiverId) {
        EscrowLink l = store.get(linkId);
        if (l == null) return 0;
        if (l.getReceiverId() != null) return 0;
        if (l.getStatus() != EscrowLinkStatus.대기) return 0;
        if (LocalDateTime.now().isAfter(l.getExpiresAt())) return 0;
        if (l.getInitiatorId().equals(receiverId)) return 0;
        ReflectionTestUtils.setField(l, "receiverId", receiverId);
        return 1;
    }
}
