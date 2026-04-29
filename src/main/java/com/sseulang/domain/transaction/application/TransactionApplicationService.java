package com.sseulang.domain.transaction.application;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemForTransactionResult;
import com.sseulang.domain.transaction.application.dto.ReviewableTransactionResult;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Transaction 거래 흐름. 가이드 §5.1 / §5.2 정합:
 *
 * <ul>
 *   <li>create: buyer 가 호출. <b>Item 비관적 락</b> + 활성(판매중) 검증 + 자기 거래 거부.
 *       reserve 동시 시 락 직렬화 — "예약 직후 새 채팅중 거래" 회귀 차단.</li>
 *   <li>reserve: seller 가 호출. <b>락 순서 Transaction → Item</b>.
 *       같은 거래에 대한 reserve vs cancel race 도 Transaction 락이 직렬화 (Codex 게이트 1 Critical 1 보강).
 *       다른 거래의 reserve 와는 Item 락이 직렬화 → status=예약 보고 거부.</li>
 *   <li>complete: <b>현재 비활성</b> — 결제·포인트 도메인 (Day 7/8) 합류 전 활성화 시 금전 정합성 결함.
 *       호출 시 {@link ErrorCode#TRANSACTION_COMPLETION_UNAVAILABLE} 503 (Codex 게이트 1 Critical 2 보강).</li>
 *   <li>cancel: 양쪽 참여자 모두 호출 가능. Transaction 락 → 예약 상태였으면 Item 복원 (예약 → 판매중).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TransactionApplicationService {

    private final TransactionRepository transactionRepository;
    private final ItemApplicationService itemApplicationService;

    public TransactionApplicationService(
            TransactionRepository transactionRepository,
            ItemApplicationService itemApplicationService
    ) {
        this.transactionRepository = transactionRepository;
        this.itemApplicationService = itemApplicationService;
    }

    @Transactional
    public Long create(TransactionCreateCommand cmd) {
        ItemForTransactionResult info = itemApplicationService.findActiveForTransaction(cmd.itemId());
        if (info.sellerId().equals(cmd.buyerId())) {
            throw new BusinessException(ErrorCode.TRANSACTION_SELF_NOT_ALLOWED);
        }
        Transaction tx = Transaction.create(
                info.itemId(),
                info.sellerId(),
                cmd.buyerId(),
                info.tradeType(),
                info.price(),
                info.deposit(),
                cmd.rentalStart(),
                cmd.rentalEnd()
        );
        return transactionRepository.save(tx).getId();
    }

    @Transactional
    public void reserve(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isSeller(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        // Item 락 + 상태 전이 — 락 순서 Transaction → Item 고정 (deadlock 회피)
        itemApplicationService.markItemAsReserved(tx.getItemId());
        tx.markAsReserved(LocalDateTime.now());
    }

    /**
     * 거래 완료 — <b>현재 비활성</b>. 결제·포인트 도메인 (Day 7/8) 합류 후 활성화.
     *
     * <p>활성화 시 흐름:
     * <ol>
     *   <li>Transaction 락 → seller 권한 검증</li>
     *   <li>Item 락 → markAsSold</li>
     *   <li>buyer 포인트 차감 → seller 포인트 적립 (atomic + id-asc 락 순서, 가이드 §4.8 §5.3)</li>
     *   <li>Transaction.markAsCompleted</li>
     * </ol>
     */
    @Transactional
    public void complete(Long transactionId, Long requesterId) {
        // Codex 게이트 1 Critical 2 — 포인트 이동 stub 인 채로 머지하면 거래완료가 되어도 돈이 안 움직임.
        // Day 7/8 결제·포인트 도메인 합류 후 본 가드를 제거하고 정식 흐름으로 교체.
        throw new BusinessException(ErrorCode.TRANSACTION_COMPLETION_UNAVAILABLE);
    }

    @Transactional
    public void cancel(Long transactionId, Long requesterId, String reason) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        boolean wasReserved = tx.getStatus() == TransactionStatus.예약;
        tx.cancel(LocalDateTime.now(), reason);
        if (wasReserved) {
            // 예약 상태였던 거래만 Item 을 판매중으로 복원 (가이드 §5.2 채팅 재활성화)
            itemApplicationService.restoreItemFromReserved(tx.getItemId());
        }
    }

    public TransactionResult getById(Long id, Long requesterId) {
        Transaction tx = findOrThrow(id);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        return TransactionResult.from(tx);
    }

    /**
     * Review 작성 시 호출. 가이드 §4.7:
     * <ul>
     *   <li>{@code status == 거래완료} 만 허용</li>
     *   <li>거래 완료 후 7일 이내</li>
     *   <li>requester 가 거래 참여자</li>
     * </ul>
     * reviewee 는 자동 결정 (seller 가 reviewer 면 buyer, 그 반대도). CLAUDE.md §3.3 — Review 도메인은
     * 본 메서드만 의존, TransactionRepository 직접 접근 X.
     */
    public ReviewableTransactionResult findCompletedForReview(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrow(transactionId);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        if (tx.getStatus() != TransactionStatus.거래완료 || tx.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.TRANSACTION_INVALID_STATE);
        }
        // 7일 경계 정확 비교 — Duration.toDays() 는 내림이라 7일 23시간도 허용되는 회귀 (Codex 게이트 2).
        if (LocalDateTime.now().isAfter(tx.getCompletedAt().plusDays(7))) {
            throw new BusinessException(ErrorCode.REVIEW_PERIOD_EXPIRED);
        }
        Long revieweeId = tx.isSeller(requesterId) ? tx.getBuyerId() : tx.getSellerId();
        return new ReviewableTransactionResult(tx.getId(), requesterId, revieweeId, tx.getCompletedAt());
    }

    private Transaction findOrThrow(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    private Transaction findOrThrowForUpdate(Long id) {
        return transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }
}
