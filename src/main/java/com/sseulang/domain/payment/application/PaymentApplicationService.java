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
    private final WebhookEventRepository webhookEventRepository;
    private final TossWebhookSignatureVerifier webhookVerifier;
    private final ObjectMapper objectMapper;
    private final WebhookPendingRateLimiter pendingRateLimiter;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            PointApplicationService pointApplicationService,
            UserApplicationService userApplicationService,
            TossProperties tossProperties,
            WebhookEventRepository webhookEventRepository,
            TossWebhookSignatureVerifier webhookVerifier,
            ObjectMapper objectMapper,
            WebhookPendingRateLimiter pendingRateLimiter
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
     * 토스 webhook 처리 — 토스 공식 spec 기반 (게이트 1 round 1 — Codex 피드백 반영 round 2).
     *
     * <p>토스 결제 webhook 공식 사항 (https://docs.tosspayments.com/reference/using-api/webhook-events):
     * <ul>
     *   <li>결제 이벤트 ({@code PAYMENT_STATUS_CHANGED}) 에는 <b>HMAC 시그니처 헤더가 없음</b> —
     *       위변조 방지는 webhook payload 자체가 아니라 토스 lookup API 재조회로 한다.</li>
     *   <li>멱등 키는 헤더 {@code tosspayments-webhook-transmission-id} (body 의 eventId 가 아님).</li>
     *   <li>HMAC 시그니처 ({@code tosspayments-webhook-signature}) 는 {@code payout.changed},
     *       {@code seller.changed} 등 일부 이벤트에만 포함 — 본 핸들러는 결제 이벤트만 처리하므로
     *       시그니처 검증은 위 두 이벤트 도입 시 활성. {@link TossWebhookSignatureVerifier} 는 골격만.</li>
     * </ul>
     *
     * <p>흐름:
     * <ol>
     *   <li>transmission-id 헤더 → 멱등 키. WebhookEvent saveAndFlush — UNIQUE 충돌 즉시 catch
     *       (lazy flush 로 트랜잭션 commit 시점에 터지는 회귀 차단).</li>
     *   <li>payload 파싱 → {@code eventType / data.paymentKey}. payload 의 status/amount 는 신뢰 X.</li>
     *   <li>{@code PAYMENT_STATUS_CHANGED} 이면 {@link PaymentGateway#lookup} 으로 토스 서버 재조회.</li>
     *   <li>lookup 결과의 status=DONE + orderId/amount 일치 검증 → markAsPaid + 포인트 적립.</li>
     * </ol>
     *
     * <p>confirm 콜백과 webhook 모두 markAsPaid 를 호출할 수 있으므로 Payment 비관적 락 + 멱등
     * (markAsPaid 가 이미 완료면 false 반환) 으로 두 경로 race 차단.</p>
     *
     * @param transmissionId 토스 발송 ID (멱등 키, 헤더 누락 시 PAYLOAD_INVALID)
     */
    @Transactional
    public void handleWebhook(String rawPayload, String transmissionId) {
        if (transmissionId == null || transmissionId.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
        }
        JsonNode root = parseOrThrow(rawPayload);
        String eventType = textOrThrow(root, "eventType");

        // DoS 차단 (게이트 1 round 2 Critical) — 결제 외 eventType 은 저장도 lookup 도 X.
        // 공격자가 random eventType + transmission-id 로 webhook_events row 폭증시키는 경로 차단.
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

        // DoS 차단 — 우리 시스템 결제가 아니거나 이미 종결된 결제면 저장 / lookup outbound 모두 X.
        // 본인 pending 으로 random paymentKey 반복 공격 차단을 위해 status=대기 만 진행 (게이트 1 round 3).
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

        // pending 유지 공격 차단 (게이트 1 round 4) — 같은 pending orderId 에 대해 60초 윈도우
        // 안 N회만 lookup outbound 허용. 토스 정상 흐름은 같은 transmission-id retry 라
        // UNIQUE 멱등에 잡혀 lookup 1회만 — 한도에 걸릴 일 없음.
        if (!pendingRateLimiter.tryAcquire(orderId)) {
            return;
        }

        // 1. 멱등 저장 — saveAndFlush 로 즉시 UNIQUE 충돌 감지 (게이트 1 round 1 Critical 4).
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

        // 2. state 동기화 — Payment 비관적 락 + lookup 재조회 + payload orderId 와 lookup orderId 일치 검증.
        boolean settled = syncPaidByWebhook(paymentKey, orderId);
        saved.markProcessed(LocalDateTime.now());
        // 정상 정산된 경우만 카운터 회수 — lookup 실패는 카운터 유지해 공격 누적 차단.
        if (settled) {
            pendingRateLimiter.release(orderId);
        }
    }

    /**
     * webhook 받은 paymentKey 로 토스 lookup 재조회 → DONE 이고 우리 Payment 와 amount/orderId
     * 일치하면 markAsPaid + 포인트 적립.
     *
     * <p>위변조 방지의 핵심 — webhook payload 의 status/amount 를 그대로 신뢰하지 않고 토스 서버에
     * 직접 물어 본다 (게이트 1 round 1 Critical 3). lookup 결과 자체가 신뢰원.</p>
     *
     * <p>이미 confirm 흐름으로 markAsPaid 됐다면 멱등 (markAsPaid false 반환). lookup orderId 가
     * 우리 DB 에 없으면 무시 (다른 가맹점 결제 잘못 라우팅 가능성).</p>
     */
    /**
     * @return true = 정상 정산 완료 (또는 confirm 으로 이미 완료된 멱등 케이스),
     *         false = lookup 실패 / orderId 불일치 등 비정상 — 호출자가 rate limit 카운터 유지.
     */
    private boolean syncPaidByWebhook(String paymentKey, String expectedOrderId) {
        // TossPaymentGateway.lookup 은 status=DONE 만 정상 반환 (DONE 아니면 PAYMENT_VERIFY_FAILED throw).
        // BusinessException 은 토스 측 의미 있는 응답(미완료/취소 등) 으로 보고 200 + skip.
        // ExternalApiException (네트워크/5xx/파싱 실패) 은 catch 안 함 → 5xx 던져 토스 retry 유도.
        PaymentConfirmResult lookup;
        try {
            lookup = paymentGateway.lookup(paymentKey);
        } catch (BusinessException e) {
            log.warn("[toss-webhook] lookup 비정상 응답 paymentKey={} code={}", paymentKey, e.getErrorCode());
            return false;
        }
        // payload orderId 와 lookup orderId 일치 검증 (게이트 1 round 3 — Critical).
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
            return true;  // confirm 흐름으로 이미 처리. 멱등 정상.
        }
        // amount 위변조 검증 — lookup amount 와 우리 저장 amount 일치 확인.
        payment.verifyAmount(lookup.amount());
        boolean newlyPaid = payment.markAsPaid(
                lookup.paymentKey(), lookup.method(), lookup.approvedAt(), lookup.rawResponse()
        );
        if (newlyPaid) {
            pointApplicationService.credit(
                    payment.getUserId(),
                    payment.getAmount(),
                    PointHistoryType.충전,
                    PointReferenceType.PAYMENT,
                    payment.getId(),
                    "토스 webhook 충전"
            );
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

    /** 관리자 결제 통계 — 완료 건수 + 누적 금액. 단일 SUM/COUNT 쿼리. */
    public PaymentStatsResult adminGetStats() {
        return new PaymentStatsResult(paymentRepository.countPaid(), paymentRepository.sumPaidAmount());
    }

    // ───────── Reconciliation (follow-up #21) ─────────

    /**
     * dangling 복구 — orderId 로 토스 lookup 시도 후 일치하면 markAsPaid + 적립.
     * Reconciliation 스케줄러가 호출. 단일 Payment 단위 트랜잭션.
     *
     * @return true = 복구 성공 (markAsPaid), false = 토스 측 미완료 / 위변조 / 매칭 실패 등
     */
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
            pointApplicationService.credit(
                    locked.getUserId(),
                    locked.getAmount(),
                    PointHistoryType.충전,
                    PointReferenceType.PAYMENT,
                    locked.getId(),
                    "토스 reconcile 복구"
            );
            log.info("[reconcile] payment#{} 복구 완료", paymentId);
        }
        return true;
    }

    /** Reconciliation 스케줄러 진입점 — stale Payment id 목록 조회. */
    public java.util.List<Long> findStalePendingIds(java.time.LocalDateTime cutoff, int limit) {
        return paymentRepository.findStalePending(cutoff, limit).stream()
                .map(Payment::getId)
                .toList();
    }

    private static String generateMerchantUid() {
        return "charge-" + UUID.randomUUID();
    }
}
