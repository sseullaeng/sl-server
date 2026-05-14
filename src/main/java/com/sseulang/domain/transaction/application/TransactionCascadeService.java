package com.sseulang.domain.transaction.application;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// B-5: escrow 종료 시 같은 chatRoom 의 직거래(non-paired) 활성 tx 일괄 거래완료.
// 격리: TransactionTemplate(REQUIRES_NEW) 명시 사용 — Spring AOP self-invocation 함정 회피.
// 한 row 실패가 escrow.settle 외부 tx 를 롤백시키지 않음 (Codex 게이트 2 후속 라운드 2 #1).
@Service
public class TransactionCascadeService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCascadeService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionTemplate newTxTemplate;
    private final Clock clock;

    public TransactionCascadeService(
            TransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.newTxTemplate = new TransactionTemplate(transactionManager);
        this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public CascadeResult cascadeCompleteByChatRoom(Long chatRoomId, Long escrowBuyerId, Long escrowSellerId) {
        if (chatRoomId == null) return CascadeResult.empty();
        // 후보 조회는 호출자(escrow.settle) 외부 tx 안 — 일관 view OK.
        List<Long> candidateIds = transactionRepository.findActiveDirectByChatRoomId(chatRoomId)
                .stream().map(Transaction::getId).toList();
        int success = 0;
        int policySkipped = 0;
        int failed = 0;
        for (Long txId : candidateIds) {
            try {
                completeOneInNewTx(txId, escrowBuyerId, escrowSellerId);
                success++;
            } catch (BusinessException biz) {
                policySkipped++;
                log.debug("[cascade] 정책 스킵 txId={} code={}", txId, biz.getErrorCode());
            } catch (RuntimeException e) {
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

    // 명시 새 트랜잭션 — TransactionTemplate 직접. AOP @Transactional 와 달리 self-invocation 함정 없음.
    private void completeOneInNewTx(Long transactionId, Long escrowBuyerId, Long escrowSellerId) {
        newTxTemplate.executeWithoutResult(status -> {
            Transaction tx = transactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            if (!tx.isBuyer(escrowBuyerId) || !tx.isSeller(escrowSellerId)) {
                throw new BusinessException(ErrorCode.TRANSACTION_FORBIDDEN);
            }
            tx.cascadeCompleteByEscrow(LocalDateTime.now(clock));
        });
    }

    public record CascadeResult(int completed, int policySkipped, int failed) {
        public static CascadeResult empty() {
            return new CascadeResult(0, 0, 0);
        }
    }
}
