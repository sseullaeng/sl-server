package com.sseulang.domain.block.application;

import com.sseulang.domain.block.domain.UserBlock;
import com.sseulang.domain.block.domain.UserBlockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryFakeUserBlockRepository implements UserBlockRepository {

    private final Map<String, UserBlock> store = new HashMap<>();
    private long sequence = 0;

    private static String key(Long blocker, Long blocked) {
        return blocker + "|" + blocked;
    }

    @Override
    public boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId) {
        return store.containsKey(key(blockerId, blockedId));
    }

    @Override
    public UserBlock save(UserBlock block) {
        if (block.getId() == null) {
            ReflectionTestUtils.setField(block, "id", ++sequence);
            ReflectionTestUtils.setField(block, "createdAt", LocalDateTime.now());
        }
        store.put(key(block.getBlockerId(), block.getBlockedId()), block);
        return block;
    }

    @Override
    public int deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId) {
        return store.remove(key(blockerId, blockedId)) != null ? 1 : 0;
    }

    @Override
    public Page<UserBlock> findByBlockerId(Long blockerId, Pageable pageable) {
        List<UserBlock> mine = store.values().stream()
                .filter(b -> b.getBlockerId().equals(blockerId))
                .sorted(Comparator.comparing(UserBlock::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int start = Math.min((int) pageable.getOffset(), mine.size());
        int end = Math.min(start + pageable.getPageSize(), mine.size());
        return new PageImpl<>(mine.subList(start, end), pageable, mine.size());
    }

    public int size() {
        return store.size();
    }
}
