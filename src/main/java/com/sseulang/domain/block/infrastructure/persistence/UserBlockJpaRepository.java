package com.sseulang.domain.block.infrastructure.persistence;

import com.sseulang.domain.block.domain.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserBlockJpaRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blockerId = :blocker AND b.blockedId = :blocked")
    int deleteByBlockerAndBlocked(@Param("blocker") Long blockerId, @Param("blocked") Long blockedId);

    Page<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId, Pageable pageable);
}
