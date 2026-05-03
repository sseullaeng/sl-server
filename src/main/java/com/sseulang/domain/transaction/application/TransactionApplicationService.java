package com.sseulang.domain.transaction.application;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemForTransactionResult;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.transaction.application.dto.PendingReviewableResult;
import com.sseulang.domain.transaction.application.dto.ReviewableTransactionResult;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.application.dto.TransactionRole;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Transaction 거래 흐름. 가이드 §5.1 / §5.2 정합:
 *
 * <ul>
 *   <li>create: buyer 가 호출. <b>Item 비관적 락</b> + 활성(판매중) 검증 + 자기 거래 거부.
 *       reserve 동시 시 락 직렬화 — "예약 직후 새 채팅중 거래" 회귀 차단.</li>
 *   <li>reserve: seller 가 호출. <b>락 순서 Transaction → Item</b>.
 *       같은 거래에 대한 reserve vs cancel race 도 Transaction 락이 직렬화 (Codex 게이트 1 Critical 1 보강).
 *       다른 거래의 reserve 와는 Item 락이 직렬화 → status=예약 보고 거부.</li>
 *   <li>complete: seller 가 호출. <b>Item 락 → markAsSold</b> 후 PointApplicationService.transfer 로
 *       buyer 차감 → seller 적립 (id-asc 락 순서, 가이드 §5.3) + history 두 건. 정산 실패 시 트랜잭션
 *       롤백으로 Item / Tx / 잔액 / history 모두 원복 (Day 8 합류, SettlementRollbackIT 가드).</li>
 *   <li>cancel: 양쪽 참여자 모두 호출 가능. Transaction 락 → 예약 상태였으면 Item 복원 (예약 → 판매중).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TransactionApplicationService {

    private final TransactionRepository transactionRepository;
    private final ItemApplicationService itemApplicationService;
    private final PointApplicationService pointApplicationService;
    private final UserApplicationService userApplicationService;
    private final java.time.Clock clock;

    public TransactionApplicationService(
            TransactionRepository transactionRepository,
            ItemApplicationService itemApplicationService,
            PointApplicationService pointApplicationService,
            UserApplicationService userApplicationService,
            java.time.Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.itemApplicationService = itemApplicationService;
        this.pointApplicationService = pointApplicationService;
        this.userApplicationService = userApplicationService;
        this.clock = clock;
    }

    @Transactional
    public Long create(TransactionCreateCommand cmd) {
        // 거래 시작은 자금 영향 — 이메일 인증 필수 (게이트 1: 미인증 사용자 차단).
        userApplicationService.requireVerified(cmd.buyerId());
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
        tx.markAsReserved(LocalDateTime.now(clock));
    }

    /**
     * 거래 완료 — Day 8 활성화. 흐름:
     * <ol>
     *   <li>Transaction 비관적 락 → seller 권한 검증 + 상태 검증 (예약 상태에서만 완료 가능)</li>
     *   <li>Item 락 → markAsSold (락 순서 Transaction → Item 고정, deadlock 방지)</li>
     *   <li>PointApplicationService.transfer — buyer 차감 → seller 적립 (id-asc 락 순서, 가이드 §5.3) + history 두 건 적재</li>
     *   <li>Transaction.markAsCompleted</li>
     * </ol>
     *
     * <p>buyer 잔액 부족 → INSUFFICIENT_POINT 트랜잭션 롤백 → seller 의 선행 적립도 함께 원복 (정합성 보장).</p>
     * <p>현재 PR 범위는 price 정산만. 대여 보증금 처리는 가이드 §4.13 — 관리자 수동 (Day 9 영역).</p>
     */
    @Transactional
    public void complete(Long transactionId, Long requesterId) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isSeller(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        // Item 락 + 상태 전이 — 락 순서 Transaction → Item 고정 (deadlock 회피, reserve/cancel 와 동일)
        itemApplicationService.markItemAsSold(tx.getItemId());
        // 포인트 정산 — price > 0 인 거래만 (나눔 거래는 0 일 수 있음, 가이드 §4.11)
        if (tx.getPrice() > 0) {
            pointApplicationService.transfer(
                    tx.getBuyerId(),
                    tx.getSellerId(),
                    tx.getPrice(),
                    PointReferenceType.TRANSACTION,
                    tx.getId(),
                    "거래 결제: tx#" + tx.getId()
            );
        }
        tx.markAsCompleted(LocalDateTime.now(clock));
    }

    @Transactional
    public void cancel(Long transactionId, Long requesterId, String reason) {
        Transaction tx = findOrThrowForUpdate(transactionId);
        if (!tx.isParticipant(requesterId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        boolean wasReserved = tx.getStatus() == TransactionStatus.예약;
        tx.cancel(LocalDateTime.now(clock), reason);
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
        if (LocalDateTime.now(clock).isAfter(tx.getCompletedAt().plusDays(7))) {
            throw new BusinessException(ErrorCode.REVIEW_PERIOD_EXPIRED);
        }
        Long revieweeId = tx.isSeller(requesterId) ? tx.getBuyerId() : tx.getSellerId();
        return new ReviewableTransactionResult(tx.getId(), requesterId, revieweeId, tx.getCompletedAt());
    }

    /**
     * 관리자 거래 통계 — total + status 별 카운트. byStatus 는 enum 모든 값 포함 (없는 status 는 0L).
     * 단일 GROUP BY 쿼리 — N+1 없음.
     */
    public TransactionStatsResult adminGetStats() {
        Map<TransactionStatus, Long> byStatus = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus s : TransactionStatus.values()) {
            byStatus.put(s, 0L);  // default 0
        }
        long total = 0;
        for (TransactionStatusCount row : transactionRepository.countGroupByStatus()) {
            byStatus.put(row.status(), row.count());
            total += row.count();
        }
        return new TransactionStatsResult(total, byStatus);
    }

    /**
     * 월별 거래완료 집계 (Admin) — completed_at 기준. 거래 0 건 월은 응답에서 0 으로 채워진다.
     * recharts 친화 — month ASC. (year, month, count, amount).
     */
    public java.util.List<com.sseulang.domain.transaction.domain.TransactionMonthlyStat> adminMonthlyTrades(
            java.time.YearMonth from, java.time.YearMonth to) {
        java.util.Map<java.time.YearMonth, com.sseulang.domain.transaction.domain.TransactionMonthlyStat> byMonth = new java.util.LinkedHashMap<>();
        for (java.time.YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            byMonth.put(m, new com.sseulang.domain.transaction.domain.TransactionMonthlyStat(m, 0, 0));
        }
        for (var row : transactionRepository.countCompletedMonthly(from, to)) {
            byMonth.put(row.month(), row);
        }
        return new java.util.ArrayList<>(byMonth.values());
    }

    /**
     * 본인이 reviewer 로 아직 작성하지 않은 거래완료 거래 페이징 (follow-up #56).
     *
     * <p>completedAt 이 7일 이내인 것만 — 작성 가능 기간이 지난 거래는 제외 (가이드 §5.5).
     * 응답 DTO 의 deadline = completedAt + 7d → 클라이언트가 남은 시간 UI 작성에 사용.</p>
     */
    /**
     * 마이페이지 내 거래 목록 — viewer 가 buyer/seller/양쪽 으로 참여한 거래 페이징.
     * role null = 양쪽, status null = 전체. 정렬: createdAt DESC + id DESC.
     */
    public Page<TransactionResult> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable) {
        return transactionRepository.findMyTransactions(userId, role, status, pageable)
                .map(TransactionResult::from);
    }

    public Page<PendingReviewableResult> findPendingReviewable(Long userId, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(7);
        return transactionRepository.findPendingReviewable(userId, since, pageable)
                .map(tx -> {
                    Long revieweeId = tx.isSeller(userId) ? tx.getBuyerId() : tx.getSellerId();
                    return new PendingReviewableResult(
                            tx.getId(),
                            tx.getItemId(),
                            revieweeId,
                            tx.getTradeType(),
                            tx.getPrice(),
                            tx.getCompletedAt(),
                            tx.getCompletedAt().plusDays(7)
                    );
                });
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
