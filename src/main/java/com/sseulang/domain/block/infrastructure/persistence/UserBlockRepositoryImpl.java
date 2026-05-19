package com.sseulang.domain.block.infrastructure.persistence;

import com.sseulang.domain.block.domain.UserBlock;
import com.sseulang.domain.block.domain.UserBlockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class UserBlockRepositoryImpl implements UserBlockRepository {

    private final UserBlockJpaRepository jpa;

    public UserBlockRepositoryImpl(UserBlockJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId) {
        return jpa.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Override
    public UserBlock save(UserBlock block) {
        return jpa.save(block);
    }

    @Override
    public int deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId) {
        return jpa.deleteByBlockerAndBlocked(blockerId, blockedId);
    }

    @Override
    public Page<UserBlock> findByBlockerId(Long blockerId, Pageable pageable) {
        return jpa.findByBlockerIdOrderByCreatedAtDesc(blockerId, pageable);
    }
}
