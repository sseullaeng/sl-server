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

/**
 * 가이드 §4.8 — 포인트 잔액 변동 + history 적재 단일 진입점. 모든 잔액 변동 (충전 / 결제 / 판매정산 /
 * 출금 / 환불) 은 본 서비스를 거쳐 user.point_balance UPDATE 와 point_histories INSERT 가 한
 * 트랜잭션으로 묶인다 — 잔액 갱신 직후 history 누락 dangling 방지.
 *
 * <p>거래 정산 (구매자 차감 → 판매자 적립) 은 {@link #transfer} 로 처리. deadlock 방지를 위해
 * 두 user 의 잔액 변동 순서를 <b>userId 오름차순</b> 으로 강제한다 (가이드 §5.3).</p>
 *
 * <p>다른 도메인은 본 서비스만 의존 — UserRepository / PointHistoryRepository 직접 호출 금지
 * (CLAUDE.md §3.3).</p>
 */
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

    /**
     * 잔액 적립 + history 적재. 충전 / 환불 (적립 방향) 호출.
     * affected!=1 인 경우 (미존재 userId) 는 UserApplicationService 내부에서 USER_NOT_FOUND 던짐 → 롤백.
     */
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

    /**
     * 잔액 차감 + history 적재. 결제 / 출금 (차감 방향) 호출.
     * 잔액 부족 시 UserApplicationService 내부에서 INSUFFICIENT_POINT 던짐 → 롤백 (history 미적재).
     */
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

    /**
     * 거래 정산 — buyer 차감 → seller 적립을 한 트랜잭션으로 묶음.
     * 가이드 §5.3 deadlock 방지: 두 user 의 잔액 변동 순서를 userId 오름차순으로 강제 (id 작은 user 먼저 락 획득).
     * <p>buyer 잔액 부족 시 INSUFFICIENT_POINT — seller 의 선행 적립도 같은 트랜잭션 안이라 함께 롤백.</p>
     */
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
        // userId 오름차순으로 잔액 변동 — deadlock 방지 (역방향 거래가 동시에 일어나도 락 순서 일관)
        if (buyerId < sellerId) {
            deduct(buyerId, amount, PointHistoryType.결제, referenceType, referenceId, description);
            credit(sellerId, amount, PointHistoryType.판매정산, referenceType, referenceId, description);
        } else {
            credit(sellerId, amount, PointHistoryType.판매정산, referenceType, referenceId, description);
            deduct(buyerId, amount, PointHistoryType.결제, referenceType, referenceId, description);
        }
    }

    /**
     * 배달 정산 — 요청자 차감 → 라이더 적립. 거래 정산({@link #transfer})과 동일하게 id-asc 순서로 락
     * 획득 (deadlock 방지). type 은 {@link PointHistoryType#배달결제}/{@link PointHistoryType#배달정산},
     * referenceType 은 {@link PointReferenceType#DELIVERY} 고정.
     *
     * <p>요청자 잔액 부족 시 INSUFFICIENT_POINT — 라이더의 선행 적립도 같은 트랜잭션 안이라 함께 롤백.</p>
     */
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
        // 정산 추적 키. point service 진입점에서 막아 history dangling 회귀 차단 (게이트 1 Suggestion).
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

    /**
     * 거래 환불 — 거래완료 상태였던 거래가 취소되는 케이스. 양쪽 잔액 원복 + 환불 history 두 건 적재.
     * id-asc 락 순서 유지 — buyer 적립 / seller 차감.
     * <p>seller 가 이미 사용/출금 등으로 잔액이 amount 미만이면 INSUFFICIENT_POINT — 운영 이슈로 escalation
     * (자동 환불 불가, Day 9 보정 절차 / 관리자 수동 처리).</p>
     */
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

    /**
     * 본인 포인트 히스토리 페이징 — 마이페이지 노출용. type 명시 시 정확 일치, null 이면 전체.
     * createdAt DESC + id DESC 안정 정렬.
     */
    public Page<PointHistoryResult> findMyHistory(Long userId, PointHistoryType type, Pageable pageable) {
        return pointHistoryRepository.findByUserIdAndType(userId, type, pageable)
                .map(PointHistoryResult::from);
    }
}
