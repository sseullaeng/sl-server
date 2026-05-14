package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.application.dto.EscrowOverdueSnapshot;
import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EscrowOverdueQueryService {

    private final EscrowApplicationRepository applicationRepository;

    public EscrowOverdueQueryService(EscrowApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public EscrowOverdueSnapshot getForOverdue(Long applicationId) {
        EscrowApplication app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCROW_NOT_FOUND));
        validateOverdueTarget(app);
        return EscrowOverdueSnapshot.from(app);
    }

    public List<EscrowOverdueSnapshot> findOverdueCandidates(LocalDateTime cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff 은 필수입니다");
        }
        return applicationRepository.findOverdueCandidates(cutoff).stream()
                .filter(this::isValidOverdueTarget)
                .map(EscrowOverdueSnapshot::from)
                .toList();
    }

    private void validateOverdueTarget(EscrowApplication app) {
        if (!isValidOverdueTarget(app)) {
            throw new BusinessException(ErrorCode.ESCROW_INVALID_STATE);
        }
    }

    private boolean isValidOverdueTarget(EscrowApplication app) {
        return app.isRentalMode()
                && (app.getStatus() == EscrowApplicationStatus.사용중
                    || app.getStatus() == EscrowApplicationStatus.반납중)
                && app.getRentalEndAt() != null
                && app.getDepositAmount() != null
                && app.getDepositAmount() >= 0;
    }
}
