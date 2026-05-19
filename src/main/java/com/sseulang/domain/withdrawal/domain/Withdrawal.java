package com.sseulang.domain.withdrawal.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Withdrawal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WithdrawalStatus status;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static Withdrawal request(
            Long userId,
            String idempotencyKey,
            long amount,
            String bankName,
            String accountNumber,
            String accountHolder,
            LocalDateTime now
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 는 양수여야 합니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        validateText(idempotencyKey, "idempotencyKey", 64);
        validateText(bankName, "bankName", 50);
        validateText(accountNumber, "accountNumber", 50);
        validateText(accountHolder, "accountHolder", 50);
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }

        Withdrawal w = new Withdrawal();
        w.userId = userId;
        
        
        w.idempotencyKey = idempotencyKey;
        w.amount = amount;
        w.bankName = bankName;
        w.accountNumber = accountNumber;
        w.accountHolder = accountHolder;
        w.status = WithdrawalStatus.신청;
        w.requestedAt = now;
        return w;
    }

    

    public void cancel(LocalDateTime now) {
        if (!status.canUserCancel()) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_NOT_CANCELABLE);
        }
        this.status = WithdrawalStatus.취소;
        this.processedAt = now;
    }

    

    public void approve(Long adminId, String memo, LocalDateTime now) {
        if (adminId == null || adminId <= 0) {
            throw new IllegalArgumentException("adminId 는 양수여야 합니다");
        }
        if (!status.canAdminProcess()) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        this.status = WithdrawalStatus.승인;
        this.adminId = adminId;
        this.adminMemo = memo;
        this.processedAt = now;
    }

    

    public void reject(Long adminId, String memo, LocalDateTime now) {
        if (adminId == null || adminId <= 0) {
            throw new IllegalArgumentException("adminId 는 양수여야 합니다");
        }
        if (!status.canAdminProcess()) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        this.status = WithdrawalStatus.거부;
        this.adminId = adminId;
        this.adminMemo = memo;
        this.processedAt = now;
    }

    

    public void markAsCompleted(LocalDateTime now) {
        if (!status.canComplete()) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        this.status = WithdrawalStatus.완료;
        this.processedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }

    private static void validateText(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 는 필수입니다");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(name + " 는 " + max + "자 이하여야 합니다");
        }
    }
}
