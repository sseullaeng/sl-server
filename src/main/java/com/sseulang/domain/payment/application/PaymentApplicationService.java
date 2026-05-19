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
import com.sseulang.domain.payment.domain.PaymentStatus;
import com.sseulang.domain.payment.domain.WebhookEvent;
import com.sseulang.domain.payment.domain.WebhookEventRepository;
import com.sseulang.domain.payment.domain.WebhookEventSource;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.infra.payment.TossProperties;
import com.sseulang.global.infra.payment.TossWebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PointApplicationService pointApplicationService;
    private final UserApplicationService userApplicationService;
    private final TossProperties tossProperties;
    private final WebhookEventRepository webhookEventRepository;
    private final TossWebhookSignatureVerifier webhookVerifier;
    private final ObjectMapper objectMapper;
    private final WebhookPendingRateLimiter pendingRateLimiter;
    private final com.sseulang.domain.escrow.application.EscrowApplicationService escrowApplicationService;
    private final com.sseulang.domain.overdue.application.OverdueApplicationService overdueApplicationService;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            PointApplicationService pointApplicationService,
            UserApplicationService userApplicationService,
            TossProperties tossProperties,
            WebhookEventRepository webhookEventRepository,
            TossWebhookSignatureVerifier webhookVerifier,
            ObjectMapper objectMapper,
            WebhookPendingRateLimiter pendingRateLimiter,
            com.sseulang.domain.escrow.application.EscrowApplicationService escrowApplicationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.sseulang.domain.overdue.application.OverdueApplicationService overdueApplicationService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.pointApplicationService = pointApplicationService;
        this.userApplicationService = userApplicationService;
        this.tossProperties = tossProperties;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookVerifier = webhookVerifier;
        this.objectMapper = objectMapper;
        this.pendingRateLimiter = pendingRateLimiter;
        this.escrowApplicationService = escrowApplicationService;
        this.overdueApplicationService = overdueApplicationService;
    }

    

    @jakarta.annotation.PostConstruct
    void warnIfSkipVerifyEnabled() {
        if (Boolean.TRUE.equals(tossProperties.skipVerify())) {
            log.warn("[!!! SKIP-VERIFY ACTIVE !!!] app.toss.skip-verify=true — 토스 confirm 검증 우회 모드 활성. "
                    + "dev/QA 한정. 운영 진입 전 반드시 false 로 복귀 (TOSS_SKIP_VERIFY 환경변수 또는 application yml).");
        }
    }

    @Transactional
    public ChargeStartResult startCharge(ChargeStartCommand cmd) {
        if (cmd.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        
        userApplicationService.requireVerified(cmd.userId());

        
        if (cmd.isEscrow()) {
            escrowApplicationService.verifyChargeIntent(cmd.escrowApplicationId(), cmd.userId(), cmd.amount());
        }

        String merchantUid = generateMerchantUid();
        Payment payment = Payment.startCharge(cmd.userId(), merchantUid, cmd.amount(), cmd.escrowApplicationId());
        Payment saved = paymentRepository.save(payment);
        return new ChargeStartResult(saved.getId(), merchantUid, cmd.amount(), tossProperties.clientKey());
    }

    

    @Transactional
    public PaymentResult confirmCharge(ChargeConfirmCommand cmd) {
        Payment payment = paymentRepository.findByMerchantUidForUpdate(cmd.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isOwnedBy(cmd.requesterId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        
        try {
            payment.verifyAmount(cmd.amount());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_AMOUNT_MISMATCH) {
                log.error("[toss-confirm][AUDIT-AMOUNT-MISMATCH-1] paymentId={} merchantUid={} "
                        + "expectedAmount={} clientAmount={} requesterId={}",
                        payment.getId(), payment.getMerchantUid(),
                        payment.getAmount(), cmd.amount(), cmd.requesterId());
            }
            throw e;
        }

        
        if (payment.getStatus().isPaid()) {
            return PaymentResult.from(payment);
        }

        
        
        if (Boolean.TRUE.equals(tossProperties.skipVerify())) {
            log.warn("[toss-confirm][SKIP-VERIFY] 토스 검증 우회 — paymentId={} merchantUid={} amount={} requesterId={}",
                    payment.getId(), payment.getMerchantUid(), cmd.amount(), cmd.requesterId());
            boolean newlyPaidSkip = payment.markAsPaid(
                    "skip-" + cmd.paymentKey(),
                    com.sseulang.domain.payment.domain.PaymentMethod.CARD,
                    LocalDateTime.now(),
                    "{\"skipped\":true,\"reason\":\"app.toss.skip-verify=true\"}"
            );
            if (newlyPaidSkip) {
                applyPaymentConfirmedEffects(payment, "토스 충전 (skip-verify, dev 전용)");
            }
            return PaymentResult.from(payment);
        }

        
        
        
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

        
        
        if (!payment.getMerchantUid().equals(result.orderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);
        }
        try {
            payment.verifyAmount(result.amount());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_AMOUNT_MISMATCH) {
                log.error("[toss-confirm][AUDIT-AMOUNT-MISMATCH-2] paymentId={} merchantUid={} "
                        + "expectedAmount={} tossAmount={} paymentKey={}",
                        payment.getId(), payment.getMerchantUid(),
                        payment.getAmount(), result.amount(), result.paymentKey());
            }
            throw e;
        }

        boolean newlyPaid = payment.markAsPaid(
                result.paymentKey(),
                result.method(),
                result.approvedAt(),
                result.rawResponse()
        );

        if (newlyPaid) {
            applyPaymentConfirmedEffects(payment, "토스 충전");
        }

        return PaymentResult.from(payment);
    }

    

    private void applyPaymentConfirmedEffects(Payment payment, String chargeDescription) {
        if (payment.getEscrowApplicationId() != null) {

            escrowApplicationService.recordPaymentConfirmed(
                    payment.getEscrowApplicationId(),
                    payment.getUserId()
            );
        } else {

            pointApplicationService.credit(
                    payment.getUserId(),
                    payment.getAmount(),
                    PointHistoryType.충전,
                    PointReferenceType.PAYMENT,
                    payment.getId(),
                    chargeDescription
            );
            // 충전 직후 buyer 의 연체 채무 우선 차감 (PR4 hook). overdueApplicationService 미주입 시 skip.
            applyOverdueDebtDeduction(payment);
        }
    }

    private void applyOverdueDebtDeduction(Payment payment) {
        if (overdueApplicationService == null) {
            return;
        }
        long debt = userApplicationService.findOverdueDebt(payment.getUserId());
        if (debt <= 0) {
            return;
        }
        long deduct = Math.min(payment.getAmount(), debt);
        try {
            pointApplicationService.deduct(
                    payment.getUserId(),
                    deduct,
                    PointHistoryType.연체채무상환,
                    PointReferenceType.OVERDUE,
                    payment.getId(),
                    "연체 채무 우선 차감"
            );
            userApplicationService.decrementOverdueDebt(payment.getUserId(), deduct);
            overdueApplicationService.recordDebtPayment(payment.getUserId(), deduct);
        } catch (RuntimeException e) {
            log.error("[overdue-debt-deduct] 채무 차감 실패 — 충전은 적용됨. paymentId={} userId={} debt={} deduct={} reason={}",
                    payment.getId(), payment.getUserId(), debt, deduct, e.getMessage(), e);
        }
    }

    

    private PaymentConfirmResult recoverByLookup(String paymentKey) {
        log.warn("[toss-recover] confirm ALREADY_PROCESSED → lookup paymentKey={}", paymentKey);
        return paymentGateway.lookup(paymentKey);
    }

    

    @Transactional
    void handleWebhook(String rawPayload, String transmissionId) {
        handleWebhook(rawPayload, transmissionId, null, null);
    }

    @Transactional
    public void handleWebhook(String rawPayload, String transmissionId, String signature, String timestamp) {
        if (transmissionId == null || transmissionId.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
        webhookVerifier.verify(rawPayload, signature, timestamp);

        JsonNode root = parseOrThrow(rawPayload);
        String eventType = textOrThrow(root, "eventType");

        
        
        if (!"PAYMENT_STATUS_CHANGED".equals(eventType)) {
            log.debug("[toss-webhook] 비결제 eventType={} — 무시", eventType);
            return;
        }

        JsonNode data = root.path("data");
        String paymentKey = data.hasNonNull("paymentKey") ? data.get("paymentKey").asText() : null;
        String orderId = data.hasNonNull("orderId") ? data.get("orderId").asText() : null;
        if (paymentKey == null || paymentKey.isBlank() || orderId == null || orderId.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }

        
        
        Payment routed = paymentRepository.findByMerchantUid(orderId).orElse(null);
        if (routed == null) {
            log.warn("[toss-webhook] 알 수 없는 orderId={} (다른 가맹점 결제) — 저장 / lookup 모두 skip", orderId);
            return;
        }
        if (routed.getStatus() != PaymentStatus.대기) {
            log.info("[toss-webhook] 이미 종결된 orderId={} status={} — webhook 무시 (DoS 가드)",
                    orderId, routed.getStatus());
            return;
        }

        
        
        
        if (!pendingRateLimiter.tryAcquire(orderId)) {
            return;
        }

        
        LocalDateTime now = LocalDateTime.now();
        WebhookEvent saved;
        try {
            saved = webhookEventRepository.save(
                    WebhookEvent.received(WebhookEventSource.TOSS, transmissionId, eventType, paymentKey, rawPayload, now)
            );
        } catch (DataIntegrityViolationException dup) {
            log.info("[toss-webhook] 중복 transmission-id={} — 멱등 응답", transmissionId);
            return;
        }

        
        boolean settled = syncPaidByWebhook(paymentKey, orderId);
        if (!settled) {
            log.debug("[toss-webhook] orderId={} 아직 처리 완료 아님 — processed_at 기록 보류", orderId);
            return;
        }

        saved.markProcessed(LocalDateTime.now());
        pendingRateLimiter.release(orderId);
    }

    

    

    private boolean syncPaidByWebhook(String paymentKey, String expectedOrderId) {
        
        
        
        PaymentConfirmResult lookup;
        try {
            lookup = paymentGateway.lookup(paymentKey);
        } catch (BusinessException e) {
            log.warn("[toss-webhook] lookup 비정상 응답 paymentKey={} code={}", paymentKey, e.getErrorCode());
            return false;
        }
        
        if (!expectedOrderId.equals(lookup.orderId())) {
            log.warn("[toss-webhook] payload orderId={} 와 lookup orderId={} 불일치 — 처리 거부",
                    expectedOrderId, lookup.orderId());
            return false;
        }
        Payment payment = paymentRepository.findByMerchantUidForUpdate(lookup.orderId()).orElse(null);
        if (payment == null) {
            log.warn("[toss-webhook] lookup orderId={} 가 우리 DB 에 없음 — 무시", lookup.orderId());
            return false;
        }
        if (payment.getStatus() == PaymentStatus.완료) {
            return true;  
        }
        
        
        
        try {
            payment.verifyAmount(lookup.amount());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_AMOUNT_MISMATCH) {
                log.error("[toss-webhook][AUDIT-AMOUNT-MISMATCH] paymentId={} merchantUid={} "
                        + "expectedAmount={} lookupAmount={} paymentKey={}",
                        payment.getId(), payment.getMerchantUid(),
                        payment.getAmount(), lookup.amount(), lookup.paymentKey());
            }
            throw e;
        }
        boolean newlyPaid = payment.markAsPaid(
                lookup.paymentKey(), lookup.method(), lookup.approvedAt(), lookup.rawResponse()
        );
        if (newlyPaid) {
            applyPaymentConfirmedEffects(payment, "토스 webhook 충전");
        }
        return true;
    }

    private JsonNode parseOrThrow(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
    }

    private static String textOrThrow(JsonNode root, String field) {
        if (!root.hasNonNull(field)) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
        String v = root.get(field).asText();
        if (v == null || v.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
        return v;
    }

    public PaymentResult getById(Long id, Long requesterId) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return PaymentResult.from(payment);
    }

    
    public PaymentStatsResult adminGetStats() {
        return new PaymentStatsResult(paymentRepository.countPaid(), paymentRepository.sumPaidAmount());
    }

    

    

    @Transactional
    public boolean reconcileStalePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.대기) {
            return false;
        }
        PaymentConfirmResult lookup;
        try {
            lookup = paymentGateway.lookupByOrderId(payment.getMerchantUid());
        } catch (BusinessException e) {
            log.debug("[reconcile] payment#{} lookup 실패 code={}", paymentId, e.getErrorCode());
            return false;
        } catch (Exception e) {
            log.warn("[reconcile] payment#{} lookup 통신 실패 — 다음 cycle 재시도", paymentId, e);
            return false;
        }
        Payment locked = paymentRepository.findByMerchantUidForUpdate(payment.getMerchantUid()).orElse(null);
        if (locked == null || locked.getStatus() != PaymentStatus.대기) {
            return false;
        }
        locked.verifyAmount(lookup.amount());
        boolean newlyPaid = locked.markAsPaid(
                lookup.paymentKey(), lookup.method(), lookup.approvedAt(), lookup.rawResponse()
        );
        if (newlyPaid) {
            applyPaymentConfirmedEffects(locked, "토스 reconcile 복구");
            log.info("[reconcile] payment#{} 복구 완료", paymentId);
        }
        return true;
    }

    
    public java.util.List<Long> findStalePendingIds(java.time.LocalDateTime cutoff, int limit) {
        return paymentRepository.findStalePending(cutoff, limit).stream()
                .map(Payment::getId)
                .toList();
    }

    private static String generateMerchantUid() {
        return "charge-" + UUID.randomUUID();
    }
}
