package com.sseulang.domain.block.application;

import com.sseulang.domain.block.application.dto.UserBlockResult;
import com.sseulang.domain.block.domain.UserBlock;
import com.sseulang.domain.block.domain.UserBlockRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserBlockApplicationService {

    private static final String UNIQUE_BLOCKER_BLOCKED = "uk_user_blocks";

    private final UserBlockRepository repository;

    public UserBlockApplicationService(UserBlockRepository repository) {
        this.repository = repository;
    }

    
    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (repository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return;
        }
        try {
            repository.save(UserBlock.create(blockerId, blockedId));
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueConflict(violation)) {
                return;
            }
            throw violation;
        }
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        repository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public Page<UserBlockResult> listMine(Long blockerId, Pageable pageable) {
        return repository.findByBlockerId(blockerId, pageable).map(UserBlockResult::from);
    }

    private static boolean isUniqueConflict(DataIntegrityViolationException violation) {
        Throwable cause = violation;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve
                    && UNIQUE_BLOCKER_BLOCKED.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == cause) return false;
            cause = next;
        }
        return false;
    }
}
