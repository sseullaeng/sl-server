package com.sseulang.domain.withdrawal.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Withdrawal Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/withdrawal/infrastructure/persistence}.
 */
public interface WithdrawalRepository {

    Withdrawal save(Withdrawal withdrawal);

    Optional<Withdrawal> findById(Long id);

    /**
     * 가이드 §5.3 — 출금 처리 시 비관적 락. 동시 승인/거부/취소 race 차단.
     */
    Optional<Withdrawal> findByIdForUpdate(Long id);

    /**
     * 권한 검증 + 락을 한 번에. 사용자 본인 자원만 락을 잡는다 — 타인 id 로 시도하면 빈 결과만 받고
     * 락은 획득되지 않음. 게이트 1: cancel 흐름의 lock-DoS 방지.
     */
    Optional<Withdrawal> findByIdAndUserIdForUpdate(Long id, Long userId);

    /**
     * 멱등성 — 동일 (userId, idempotencyKey) 의 기존 신청 조회. 같은 key 재요청 시 새 행 만들지 않고
     * 기존 행을 그대로 반환하기 위해 사용. UNIQUE(user_id, idempotency_key) 와 짝.
     */
    Optional<Withdrawal> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Withdrawal> findByUserId(Long userId, Pageable pageable);

    /** 관리자 — 상태별 페이징 (status null 이면 전체). */
    Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable);

    // ───────── 관리자 통계 ─────────

    List<WithdrawalStatusCount> countGroupByStatus();

    /** status=완료 출금의 amount 합계 (실제 외부 이체된 금액). */
    long sumCompletedAmount();
}
