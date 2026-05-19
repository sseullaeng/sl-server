package com.sseulang.domain.withdrawal.application;

import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalRequestCommand;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalResult;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;
import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalRepository;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatusCount;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class WithdrawalApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalApplicationService.class);

    private final WithdrawalRepository withdrawalRepository;
    private final PointApplicationService pointApplicationService;
    private final UserApplicationService userApplicationService;
    

    private final WithdrawalApplicationService self;

    @Autowired
    public WithdrawalApplicationService(
            WithdrawalRepository withdrawalRepository,
            PointApplicationService pointApplicationService,
            UserApplicationService userApplicationService,
            @Lazy WithdrawalApplicationService self
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.pointApplicationService = pointApplicationService;
        this.userApplicationService = userApplicationService;
        this.self = self;
    }

    

    public Long request(WithdrawalRequestCommand cmd) {
        if (cmd.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        
        userApplicationService.requireVerified(cmd.userId());

        
        var existing = withdrawalRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey());
        if (existing.isPresent()) {
            assertSamePayload(existing.get(), cmd);
            return existing.get().getId();
        }

        
        
        
        
        
        try {
            return self.insertWithDeduct(cmd);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException race) {
            
            
            
            return self.findIdempotentInFreshTx(cmd, race);
        }
    }

    

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findIdempotentInFreshTx(WithdrawalRequestCommand cmd, RuntimeException original) {
        Withdrawal w = withdrawalRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey())
                .orElseThrow(() -> original);
        assertSamePayload(w, cmd);
        return w.getId();
    }

    

    private static void assertSamePayload(Withdrawal existing, WithdrawalRequestCommand cmd) {
        if (existing.getAmount() != cmd.amount()
                || !existing.getBankName().equals(cmd.bankName())
                || !existing.getAccountNumber().equals(cmd.accountNumber())
                || !existing.getAccountHolder().equals(cmd.accountHolder())) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_IDEMPOTENCY_MISMATCH);
        }
    }

    

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insertWithDeduct(WithdrawalRequestCommand cmd) {
        Withdrawal saved = withdrawalRepository.save(Withdrawal.request(
                cmd.userId(), cmd.idempotencyKey(), cmd.amount(),
                cmd.bankName(), cmd.accountNumber(), cmd.accountHolder(),
                LocalDateTime.now()
        ));
        pointApplicationService.deduct(
                cmd.userId(), cmd.amount(),
                PointHistoryType.출금,
                PointReferenceType.WITHDRAWAL,
                saved.getId(),
                "출금 신청"
        );
        return saved.getId();
    }

    @Transactional
    public void cancel(Long withdrawalId, Long requesterId) {
        
        
        Withdrawal w = withdrawalRepository.findByIdAndUserIdForUpdate(withdrawalId, requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        
        w.cancel(LocalDateTime.now());
        refund(w, "출금 신청 취소");
    }

    @Transactional
    public void adminApprove(Long withdrawalId, Long adminId, String memo) {
        Withdrawal w = findOrThrowForUpdate(withdrawalId);
        w.approve(adminId, memo, LocalDateTime.now());
        
    }

    @Transactional
    public void adminReject(Long withdrawalId, Long adminId, String memo) {
        Withdrawal w = findOrThrowForUpdate(withdrawalId);
        w.reject(adminId, memo, LocalDateTime.now());
        refund(w, "출금 신청 거부");
    }

    

    @Transactional
    public void adminComplete(Long withdrawalId, Long adminId) {
        Withdrawal w = findOrThrowForUpdate(withdrawalId);
        w.markAsCompleted(LocalDateTime.now());
        log.info("[withdrawal-complete] id={} amount={} adminId={} (external transfer mock)",
                w.getId(), w.getAmount(), adminId);
    }

    public WithdrawalResult getById(Long id, Long requesterId) {
        Withdrawal w = withdrawalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        if (!w.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_FORBIDDEN);
        }
        return WithdrawalResult.from(w);
    }

    public Page<WithdrawalResult> findMyWithdrawals(Long userId, Pageable pageable) {
        return withdrawalRepository.findByUserId(userId, pageable).map(WithdrawalResult::from);
    }

    public Page<WithdrawalResult> adminFindByStatus(WithdrawalStatus status, Pageable pageable) {
        return withdrawalRepository.findByStatus(status, pageable).map(WithdrawalResult::from);
    }

    
    public WithdrawalStatsResult adminGetStats() {
        Map<WithdrawalStatus, Long> byStatus = new EnumMap<>(WithdrawalStatus.class);
        for (WithdrawalStatus s : WithdrawalStatus.values()) {
            byStatus.put(s, 0L);
        }
        long total = 0;
        for (WithdrawalStatusCount row : withdrawalRepository.countGroupByStatus()) {
            byStatus.put(row.status(), row.count());
            total += row.count();
        }
        long completedAmount = withdrawalRepository.sumCompletedAmount();
        return new WithdrawalStatsResult(total, byStatus, completedAmount);
    }

    private void refund(Withdrawal w, String description) {
        
        pointApplicationService.credit(
                w.getUserId(), w.getAmount(),
                PointHistoryType.환불,
                PointReferenceType.WITHDRAWAL,
                w.getId(),
                description
        );
    }

    private Withdrawal findOrThrowForUpdate(Long id) {
        return withdrawalRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WITHDRAWAL_NOT_FOUND));
    }
}
