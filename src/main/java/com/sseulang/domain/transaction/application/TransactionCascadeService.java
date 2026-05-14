package com.sseulang.domain.transaction.application;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// B-5: escrow 종료 시 같은 chatRoom 의 직거래(non-paired) 활성 tx 일괄 거래완료.
// 별도 컴포넌트 — 내부 self-invocation 회피. 각 row 는 REQUIRES_NEW 새 tx 로 격리되어
// 한 건 실패가 escrow settle 외부 tx 를 롤백시키지 않음 (Codex 게이트 2 후속 #1).
@Service
public class TransactionCascadeService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCascadeService.class);

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public TransactionCascadeService(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public CascadeResult cascadeCompleteByChatRoom(Long chatRoomId, Long escrowBuyerId, Long escrowSellerId) {
        if (chatRoomId == null) return CascadeResult.empty();
        List<Long> candidateIds = transactionRepository.findActiveDirectByChatRoomId(chatRoomId)
                .stream().map(Transaction::getId).toList();
        int success = 0;
        int policySkipped = 0;
        int failed = 0;
        for (Long txId : candidateIds) {
            try {
                completeOne(txId, escrowBuyerId, escrowSellerId);
                success++;
            } catch (BusinessException biz) {
                // 정책 가드(대여/권한 불일치/종료 상태) 거부 — 정상 흐름.
                policySkipped++;
                log.debug("[cascade] 정책 스킵 txId={} code={}", txId, biz.getErrorCode());
            } catch (RuntimeException e) {
                // 인프라(DB/락/JPA) 실패 — 추적 가능하도록 명시 로깅.
                failed++;
                log.warn("[cascade] 인프라 실패 txId={} chatRoomId={} — zombie 가능성", txId, chatRoomId, e);
            }
        }
        if (success > 0 || failed > 0) {
            log.info("[cascade] chatRoomId={} 후보 {}건 — 성공 {} / 정책스킵 {} / 인프라실패 {}",
                    chatRoomId, candidateIds.size(), success, policySkipped, failed);
        }
        return new CascadeResult(success, policySkipped, failed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeOne(Long transactionId, Long escrowBuyerId, Long escrowSellerId) {
        Transaction tx = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        if (!tx.isBuyer(escrowBuyerId) || !tx.isSeller(escrowSellerId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
        }
        tx.cascadeCompleteByEscrow(LocalDateTime.now(clock));
    }

    public record CascadeResult(int completed, int policySkipped, int failed) {
        public static CascadeResult empty() {
            return new CascadeResult(0, 0, 0);
        }
    }
}
