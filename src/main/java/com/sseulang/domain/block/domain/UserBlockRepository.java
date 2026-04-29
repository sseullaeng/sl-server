package com.sseulang.domain.block.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserBlockRepository {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    UserBlock save(UserBlock block);

    /** 멱등 삭제. 영향 행 수 반환. */
    int deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Page<UserBlock> findByBlockerId(Long blockerId, Pageable pageable);
}
