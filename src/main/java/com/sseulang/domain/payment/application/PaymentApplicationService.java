package com.sseulang.domain.payment.application;

import com.sseulang.domain.payment.application.dto.ChargeConfirmCommand;
import com.sseulang.domain.payment.application.dto.ChargeStartCommand;
import com.sseulang.domain.payment.application.dto.ChargeStartResult;
import com.sseulang.domain.payment.application.dto.PaymentResult;
import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.payment.domain.Payment;
import com.sseulang.domain.payment.domain.PaymentConfirmResult;
import com.sseulang.domain.payment.domain.PaymentGateway;
import com.sseulang.domain.payment.domain.PaymentRepository;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.infra.payment.TossProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 가이드 §4.9 결제 흐름 — 토스페이먼츠 실연동:
 *
 * <ol>
 *   <li>{@link #startCharge}: merchant_uid 발급 + Payment 저장 (status=대기). 클라이언트가 토스 위젯으로 결제 진행.</li>
 *   <li>{@link #confirmCharge}: 토스 redirect 후 백엔드 콜백. Payment 비관적 락 → amount 위변조 검증 →
 *       토스 confirm API 호출 → 응답 재검증 → markAsPaid + 포인트 atomic 적립. 멱등 — 이미 완료면 노op.</li>
 *   <li>{@link #handleWebhook}: 토스 webhook 수신 — 본 PR 에선 로깅만. confirm 으로 처리되므로 보강용.</li>
 * </ol>
 *
 * <p><b>위변조 방지 2중</b>: 클라가 보낸 amount 와 Payment 저장 amount 일치 + 토스 응답 amount 와 Payment amount 일치.</p>
 */
@Service
@Transactional(readOnly = true)
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PointApplicationService pointApplicationService;
    private final UserApplicationService userApplicationService;
    private final TossProperties tossProperties;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            PointApplicationService pointApplicationService,
            UserApplicationService userApplicationService,
            TossProperties tossProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.pointApplicationService = pointApplicationService;
        this.userApplicationService = userApplicationService;
        this.tossProperties = tossProperties;
    }

    @Transactional
    public ChargeStartResult startCharge(ChargeStartCommand cmd) {
        if (cmd.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 자금 흐름 진입점 — 이메일 인증 미완료 사용자 차단 (게이트 1).
        userApplicationService.requireVerified(cmd.userId());
        String merchantUid = generateMerchantUid();
        Payment payment = Payment.startCharge(cmd.userId(), merchantUid, cmd.amount());
        Payment saved = paymentRepository.save(payment);
        return new ChargeStartResult(saved.getId(), merchantUid, cmd.amount(), tossProperties.clientKey());
    }

    /**
     * 토스 redirect 후 백엔드 confirm. 비관적 락 + 멱등 + 위변조 검증 + 토스 호출 + 포인트 적립.
     */
    @Transactional
    public PaymentResult confirmCharge(ChargeConfirmCommand cmd) {
        Payment payment = paymentRepository.findByMerchantUidForUpdate(cmd.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isOwnedBy(cmd.requesterId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 1차 검증 — 클라가 보낸 amount 와 저장 amount 일치 (위변조 방지).
        payment.verifyAmount(cmd.amount());

        // 멱등 — 이미 완료된 결제는 토스 호출 없이 그대로 반환.
        if (payment.getStatus().isPaid()) {
            return PaymentResult.from(payment);
        }

        // 토스 confirm API 호출. 4xx 분기:
        //   - PAYMENT_ALREADY_PROCESSED → lookup 으로 상태 동기화 (dangling 자동 복구, Codex 게이트 1 ④)
        //   - 그 외 → 그대로 throw
        PaymentConfirmResult result;
        try {
            result = paymentGateway.confirm(cmd.paymentKey(), cmd.orderId(), cmd.amount());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_ALREADY_PROCESSED) {
                result = recoverByLookup(cmd.paymentKey());
            } else {
                throw e;
            }
        }

        // 2차 검증 — 토스 응답 orderId / amount 가 저장값과 일치 (confirm 정상 / lookup 복구 양 경로 공통).
        // orderId 검증은 lookup 복구 시 다른 paymentKey-orderId 페어가 우리 결제와 매칭되는 위변조 차단 (Codex 게이트 1 2차).
        if (!payment.getMerchantUid().equals(result.orderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
        payment.verifyAmount(result.amount());

        boolean newlyPaid = payment.markAsPaid(
                result.paymentKey(),
                result.method(),
                result.approvedAt(),
                result.rawResponse()
        );

        if (newlyPaid) {
            // 가이드 §4.8 — 충전 성공 시 atomic point_balance 증가 + history 적재 (Day 8 PointApplicationService 도입).
            pointApplicationService.credit(
                    payment.getUserId(),
                    payment.getAmount(),
                    PointHistoryType.충전,
                    PointReferenceType.PAYMENT,
                    payment.getId(),
                    "토스 충전"
            );
        }

        return PaymentResult.from(payment);
    }

    /**
     * 토스가 confirm 4xx ALREADY_PROCESSED 응답한 케이스 — 이미 PG 측에서 승인된 상태라
     * paymentKey 단건 조회로 결과를 복구해서 markAsPaid + 포인트 적립까지 동기화한다.
     * lookup 결과 amount 가 우리 저장 amount 와 다르면 verifyAmount 가 PAYMENT_AMOUNT_MISMATCH 던짐 (위변조 차단).
     */
    private PaymentConfirmResult recoverByLookup(String paymentKey) {
        log.warn("[toss-recover] confirm ALREADY_PROCESSED → lookup paymentKey={}", paymentKey);
        return paymentGateway.lookup(paymentKey);
    }

    /**
     * 토스 webhook 수신 — 본 PR 에선 로깅만. 정식 처리(서명 검증 + 멱등 + state 동기화)는 후속.
     */
    public void handleWebhook(String rawPayload) {
        log.info("[toss-webhook] {}", rawPayload);
    }

    public PaymentResult getById(Long id, Long requesterId) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return PaymentResult.from(payment);
    }

    /** 관리자 결제 통계 — 완료 건수 + 누적 금액. 단일 SUM/COUNT 쿼리. */
    public PaymentStatsResult adminGetStats() {
        return new PaymentStatsResult(paymentRepository.countPaid(), paymentRepository.sumPaidAmount());
    }

    private static String generateMerchantUid() {
        return "charge-" + UUID.randomUUID();
    }
}
