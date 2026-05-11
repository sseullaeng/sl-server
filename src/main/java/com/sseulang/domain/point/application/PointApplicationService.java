package com.sseulang.domain.point.application;

import com.sseulang.domain.point.application.dto.PointHistoryResult;
import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class PointApplicationService {

    private final UserApplicationService userApplicationService;
    private final PointHistoryRepository pointHistoryRepository;

    public PointApplicationService(
            UserApplicationService userApplicationService,
            PointHistoryRepository pointHistoryRepository
    ) {
        this.userApplicationService = userApplicationService;
        this.pointHistoryRepository = pointHistoryRepository;
    }

    

    @Transactional
    public void credit(
            Long userId,
            long amount,
            PointHistoryType type,
            PointReferenceType referenceType,
            Long referenceId,
            String description
    ) {
        userApplicationService.creditPoint(userId, amount);
        long balanceAfter = readBalance(userId);
        pointHistoryRepository.save(PointHistory.recordCredit(
                userId, type, amount, balanceAfter, referenceType, referenceId, description, LocalDateTime.now()
        ));
    }

    

    @Transactional
    public void deduct(
            Long userId,
            long amount,
            PointHistoryType type,
            PointReferenceType referenceType,
            Long referenceId,
            String description
    ) {
        userApplicationService.deductPoint(userId, amount);
        long balanceAfter = readBalance(userId);
        pointHistoryRepository.save(PointHistory.recordDebit(
                userId, type, amount, balanceAfter, referenceType, referenceId, description, LocalDateTime.now()
        ));
    }

    

    @Transactional
    public void transfer(
            Long buyerId,
            Long sellerId,
            long amount,
            PointReferenceType referenceType,
            Long referenceId,
            String description
    ) {
        if (buyerId == null || sellerId == null) {
            throw new IllegalArgumentException("buyerId / sellerId 는 필수입니다");
        }
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("buyer 와 seller 는 같을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        
        if (buyerId < sellerId) {
            deduct(buyerId, amount, PointHistoryType.결제, referenceType, referenceId, description);
            credit(sellerId, amount, PointHistoryType.판매정산, referenceType, referenceId, description);
        } else {
            credit(sellerId, amount, PointHistoryType.판매정산, referenceType, referenceId, description);
            deduct(buyerId, amount, PointHistoryType.결제, referenceType, referenceId, description);
        }
    }

    

    @Transactional
    public void transferForDelivery(
            Long requesterId,
            Long riderId,
            long amount,
            Long deliveryId,
            String description
    ) {
        if (requesterId == null || riderId == null) {
            throw new IllegalArgumentException("requesterId / riderId 는 필수입니다");
        }
        if (requesterId.equals(riderId)) {
            throw new IllegalArgumentException("requester 와 rider 는 같을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        
        if (deliveryId == null || deliveryId <= 0) {
            throw new IllegalArgumentException("deliveryId 는 양수여야 합니다");
        }
        if (requesterId < riderId) {
            deduct(requesterId, amount, PointHistoryType.배달결제, PointReferenceType.DELIVERY, deliveryId, description);
            credit(riderId, amount, PointHistoryType.배달정산, PointReferenceType.DELIVERY, deliveryId, description);
        } else {
            credit(riderId, amount, PointHistoryType.배달정산, PointReferenceType.DELIVERY, deliveryId, description);
            deduct(requesterId, amount, PointHistoryType.배달결제, PointReferenceType.DELIVERY, deliveryId, description);
        }
    }

    

    @Transactional
    public void refund(
            Long buyerId,
            Long sellerId,
            long amount,
            PointReferenceType referenceType,
            Long referenceId,
            String description
    ) {
        if (buyerId == null || sellerId == null) {
            throw new IllegalArgumentException("buyerId / sellerId 는 필수입니다");
        }
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("buyer 와 seller 는 같을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        if (buyerId < sellerId) {
            creditRefund(buyerId, amount, referenceType, referenceId, description);
            deductRefund(sellerId, amount, referenceType, referenceId, description);
        } else {
            deductRefund(sellerId, amount, referenceType, referenceId, description);
            creditRefund(buyerId, amount, referenceType, referenceId, description);
        }
    }

    private void creditRefund(Long userId, long amount, PointReferenceType refType, Long refId, String description) {
        userApplicationService.creditPoint(userId, amount);
        long balanceAfter = readBalance(userId);
        pointHistoryRepository.save(PointHistory.recordCredit(
                userId, PointHistoryType.환불, amount, balanceAfter, refType, refId, description, LocalDateTime.now()
        ));
    }

    private void deductRefund(Long userId, long amount, PointReferenceType refType, Long refId, String description) {
        userApplicationService.deductPoint(userId, amount);
        long balanceAfter = readBalance(userId);
        pointHistoryRepository.save(PointHistory.recordDebit(
                userId, PointHistoryType.환불, amount, balanceAfter, refType, refId, description, LocalDateTime.now()
        ));
    }

    private long readBalance(Long userId) {
        return userApplicationService.getPointBalance(userId);
    }

    

    public Page<PointHistoryResult> findMyHistory(Long userId, PointHistoryType type, Pageable pageable) {
        return pointHistoryRepository.findByUserIdAndType(userId, type, pageable)
                .map(PointHistoryResult::from);
    }

    

    

    @Transactional
    public void escrowHold(Long buyerId, long amount, Long transactionId, String description) {
        if (buyerId == null || buyerId <= 0) {
            throw new IllegalArgumentException("buyerId 는 양수여야 합니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException("transactionId 는 양수여야 합니다");
        }
        userApplicationService.holdForEscrow(buyerId, amount);
        long balanceAfter = readBalance(buyerId);
        pointHistoryRepository.save(PointHistory.recordDebit(
                buyerId, PointHistoryType.거래보관, amount, balanceAfter,
                PointReferenceType.TRANSACTION, transactionId, description, LocalDateTime.now()
        ));
    }

    

    @Transactional
    public void escrowRelease(
            Long buyerId,
            Long sellerId,
            long amount,
            Long transactionId,
            String description
    ) {
        if (buyerId == null || sellerId == null) {
            throw new IllegalArgumentException("buyerId / sellerId 는 필수입니다");
        }
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("buyer 와 seller 는 같을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException("transactionId 는 양수여야 합니다");
        }
        if (buyerId < sellerId) {
            userApplicationService.releaseHold(buyerId, amount);
            creditSellerSettlement(sellerId, amount, transactionId, description);
        } else {
            creditSellerSettlement(sellerId, amount, transactionId, description);
            userApplicationService.releaseHold(buyerId, amount);
        }
    }

    

    @Transactional
    public void escrowRefund(Long buyerId, long amount, Long transactionId, String description) {
        if (buyerId == null || buyerId <= 0) {
            throw new IllegalArgumentException("buyerId 는 양수여야 합니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException("transactionId 는 양수여야 합니다");
        }
        userApplicationService.refundHold(buyerId, amount);
        long balanceAfter = readBalance(buyerId);
        pointHistoryRepository.save(PointHistory.recordCredit(
                buyerId, PointHistoryType.거래환불, amount, balanceAfter,
                PointReferenceType.TRANSACTION, transactionId, description, LocalDateTime.now()
        ));
    }

    private void creditSellerSettlement(Long sellerId, long amount, Long transactionId, String description) {
        userApplicationService.creditPoint(sellerId, amount);
        long balanceAfter = readBalance(sellerId);
        pointHistoryRepository.save(PointHistory.recordCredit(
                sellerId, PointHistoryType.판매정산, amount, balanceAfter,
                PointReferenceType.TRANSACTION, transactionId, description, LocalDateTime.now()
        ));
    }
}
