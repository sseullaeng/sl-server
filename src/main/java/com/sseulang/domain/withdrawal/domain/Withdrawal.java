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

/**
 * Withdrawal Aggregate Root. V1 스키마 {@code withdrawals} 매핑.
 *
 * <p>가이드 §4.8 출금: 사용자 신청 → 관리자 승인 → 외부 계좌 이체 (시뮬). 잔액 정합성은
 * ApplicationService 가 PointApplicationService 와 한 트랜잭션으로 묶어 처리. 본 Aggregate 는
 * 상태 머신만 책임.</p>
 *
 * <p>Setter 없음. 상태 변경은 명시 비즈니스 메서드만.</p>
 */
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

    /**
     * 멱등성 키 — 클라이언트가 신청 단위마다 발급. 동일 (user_id, idempotency_key) 재요청 시
     * ApplicationService 가 기존 row 를 그대로 반환 (DB UNIQUE 가 race 의 마지막 가드).
     * nullable — V4 마이그레이션 이전 데이터(없음) 호환 여지지만 실제로는 항상 채워서 들어옴.
     */
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
        // 모든 문자열 필드는 Command compact constructor 에서 이미 trim/검증됨 — 여기서 다시 trim 하면
        // 조회/저장 값 불일치로 멱등성 dedup 이 거짓 거부될 수 있어 그대로 저장 (게이트 2 round 2).
        w.idempotencyKey = idempotencyKey;
        w.amount = amount;
        w.bankName = bankName;
        w.accountNumber = accountNumber;
        w.accountHolder = accountHolder;
        w.status = WithdrawalStatus.신청;
        w.requestedAt = now;
        return w;
    }

    /**
     * 사용자가 직접 취소. 신청 상태만 허용. 호출자가 잔액 원복 적용.
     */
    public void cancel(LocalDateTime now) {
        if (!status.canUserCancel()) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_NOT_CANCELABLE);
        }
        this.status = WithdrawalStatus.취소;
        this.processedAt = now;
    }

    /**
     * 관리자 승인 — 신청 → 승인. 외부 이체는 후속 {@link #markAsCompleted} 가 처리.
     */
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

    /**
     * 관리자 거부 — 신청 → 거부. 호출자가 잔액 원복 적용.
     */
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

    /**
     * 관리자 외부 이체 완료 처리 — 승인 → 완료. 외부 이체 mock 후 호출.
     */
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
