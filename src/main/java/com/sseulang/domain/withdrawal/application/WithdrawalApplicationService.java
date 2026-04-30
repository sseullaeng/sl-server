package com.sseulang.domain.withdrawal.application;

import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalRequestCommand;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalResult;
import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalRepository;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
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

/**
 * 가이드 §4.8 출금 흐름:
 *
 * <ol>
 *   <li>{@link #request} — 사용자 신청. 잔액 차감 ({@code PointApplicationService.deduct(type=출금, refType=WITHDRAWAL)})
 *       + Withdrawal 저장 (status=신청). 잔액 부족 → INSUFFICIENT_POINT 트랜잭션 롤백.</li>
 *   <li>{@link #cancel} — 사용자 본인 취소. 신청 상태만 허용. 잔액 원복 (환불 history 적재).</li>
 *   <li>{@link #adminApprove} — 관리자 승인. 신청 → 승인. 외부 이체는 후속 {@link #adminComplete} 가 처리.</li>
 *   <li>{@link #adminReject} — 관리자 거부. 잔액 원복.</li>
 *   <li>{@link #adminComplete} — 관리자 외부 이체 완료. 승인 → 완료.</li>
 * </ol>
 *
 * <p>모든 상태 전이는 {@code findByIdForUpdate} 비관적 락으로 직렬화 (가이드 §5.3).</p>
 */
@Service
@Transactional(readOnly = true)
public class WithdrawalApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalApplicationService.class);

    private final WithdrawalRepository withdrawalRepository;
    private final PointApplicationService pointApplicationService;
    /**
     * 자기 자신 proxy — {@link #request} 가 {@link #insertWithDeduct} 를 별도 트랜잭션으로 호출하기 위해
     * 사용한다. 직접 {@code this.insertWithDeduct(...)} 로 호출하면 Spring AOP 가 가로채지 못해
     * REQUIRES_NEW 의미가 깨진다. {@code @Lazy} 로 순환 의존 회피.
     */
    private final WithdrawalApplicationService self;

    @Autowired
    public WithdrawalApplicationService(
            WithdrawalRepository withdrawalRepository,
            PointApplicationService pointApplicationService,
            @Lazy WithdrawalApplicationService self
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.pointApplicationService = pointApplicationService;
        this.self = self;
    }

    /**
     * 출금 신청 — 멱등성 보장.
     *
     * <p>흐름:
     * <ol>
     *   <li>같은 (userId, idempotencyKey) 가 이미 있으면 그 id 즉시 반환 (no-op).</li>
     *   <li>없으면 {@link #insertWithDeduct} 를 REQUIRES_NEW 로 호출. INSERT 가 동시 race 로
     *       실패(unique 위반 / deadlock)하면 그 트랜잭션만 롤백되고 본 readonly 외부 tx 는 영향 없음.</li>
     *   <li>패배자 경로: 다시 조회 → winner 가 commit 한 row 의 id 반환.</li>
     * </ol>
     *
     * <p>왜 REQUIRES_NEW 인가: 같은 tx 안에서 INSERT 실패를 catch 하면 Hibernate persistence
     * context 가 dirty 상태로 남아 후속 flush 가 깨진다 (AssertionFailure: don't flush after exception).
     * 별도 tx 로 격리해 winner 와 loser 각자 깔끔하게 commit/rollback 하도록 처리.</p>
     */
    public Long request(WithdrawalRequestCommand cmd) {
        if (cmd.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 1) Fast path — 기존 신청 dedup. payload 가 다르면 클라이언트 버그 시그널 → 명시 거부.
        var existing = withdrawalRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey());
        if (existing.isPresent()) {
            assertSamePayload(existing.get(), cmd);
            return existing.get().getId();
        }

        // 2) 새 행 + 차감을 별 tx 에서. 동시 INSERT 가 UNIQUE 제약을 노릴 때 던질 수 있는 모든
        //    예외를 dedup 시그널로 처리:
        //    - DataIntegrityViolationException: duplicate-key (가장 일반)
        //    - PessimisticLockingFailureException 계열 (CannotAcquireLockException /
        //      DeadlockLoserDataAccessException 포함): InnoDB gap lock + deadlock 패턴
        try {
            return self.insertWithDeduct(cmd);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException race) {
            // 3) 패배자 — winner 가 commit 한 상태에서 재조회. 단, 본 outer tx 가 REPEATABLE_READ
            //    snapshot 을 쥐고 있으면 winner 의 새 row 가 보이지 않으므로 REQUIRES_NEW 로 fresh
            //    스냅샷을 받아서 조회 (fix #2).
            return self.findIdempotentInFreshTx(cmd, race);
        }
    }

    /**
     * race 패배자 보정 — outer tx 의 snapshot 으로는 winner 의 새 row 가 보이지 않을 수 있어
     * 새 트랜잭션으로 재조회한다 (REPEATABLE_READ 환경에서도 안전).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findIdempotentInFreshTx(WithdrawalRequestCommand cmd, RuntimeException original) {
        Withdrawal w = withdrawalRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey())
                .orElseThrow(() -> original);
        assertSamePayload(w, cmd);
        return w.getId();
    }

    /**
     * 같은 idempotencyKey 로 다른 내용(amount/계좌)이 들어오면 클라이언트 버그 가능성 — 조용히
     * 기존 id 반환하는 대신 명시적으로 거부 (게이트 2: silent pass 방지).
     */
    private static void assertSamePayload(Withdrawal existing, WithdrawalRequestCommand cmd) {
        if (existing.getAmount() != cmd.amount()
                || !existing.getBankName().equals(cmd.bankName())
                || !existing.getAccountNumber().equals(cmd.accountNumber())
                || !existing.getAccountHolder().equals(cmd.accountHolder())) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_IDEMPOTENCY_MISMATCH);
        }
    }

    /**
     * 신규 INSERT + 잔액 차감을 한 트랜잭션으로 묶는다. 잔액 부족 시 INSUFFICIENT_POINT → 본 tx 만
     * 롤백 (Withdrawal save 도 함께 원복). UNIQUE race 패배 시도 본 tx 만 롤백되어 외부에 영향 없음.
     *
     * <p>Spring AOP 통과를 위해 반드시 {@link #self} 경유로만 호출.</p>
     */
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
        // 본인 자원만 락 + 권한 검증을 한 쿼리로 — 타인 id 로는 락이 잡히지 않아 lock-DoS 차단 (게이트 1).
        // 행이 존재하지만 소유자가 다른 경우와, 행 자체가 없는 경우를 클라이언트에 동일하게 응답 (존재 여부 leak X).
        Withdrawal w = withdrawalRepository.findByIdAndUserIdForUpdate(withdrawalId, requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        // 신청 상태만 허용 (Aggregate 가 가드). 신청 → 취소 + 잔액 원복.
        w.cancel(LocalDateTime.now());
        refund(w, "출금 신청 취소");
    }

    @Transactional
    public void adminApprove(Long withdrawalId, Long adminId, String memo) {
        Withdrawal w = findOrThrowForUpdate(withdrawalId);
        w.approve(adminId, memo, LocalDateTime.now());
        // 잔액은 신청 시점에 이미 차감되어 있으므로 별도 변동 X. 외부 이체는 adminComplete 단계.
    }

    @Transactional
    public void adminReject(Long withdrawalId, Long adminId, String memo) {
        Withdrawal w = findOrThrowForUpdate(withdrawalId);
        w.reject(adminId, memo, LocalDateTime.now());
        refund(w, "출금 신청 거부");
    }

    /**
     * 관리자 외부 이체 완료 처리. 본 PR 에선 이체 자체는 mock — 외부 은행 API 연동은 5/6 이후 후속.
     * 승인 → 완료 단순 상태 전이.
     */
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

    private void refund(Withdrawal w, String description) {
        // 신청 시점 차감과 동일 트랜잭션 안에서 환불. 잔액 변경 + history 적재 (type=환불, refType=WITHDRAWAL).
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
