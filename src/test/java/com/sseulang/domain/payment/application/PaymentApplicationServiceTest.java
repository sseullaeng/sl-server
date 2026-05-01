package com.sseulang.domain.payment.application;

import com.sseulang.domain.payment.application.dto.ChargeConfirmCommand;
import com.sseulang.domain.payment.application.dto.ChargeStartCommand;
import com.sseulang.domain.payment.application.dto.ChargeStartResult;
import com.sseulang.domain.payment.application.dto.PaymentResult;
import com.sseulang.domain.payment.domain.PaymentStatus;
import com.sseulang.domain.point.application.InMemoryFakePointHistoryRepository;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.user.application.InMemoryFakeUserRepository;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.infra.payment.TossProperties;
import com.sseulang.global.infra.payment.TossWebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentApplicationServiceTest {

    private static final String CLIENT_KEY = "test_ck_1234567890";

    private InMemoryFakePaymentRepository paymentRepo;
    private FakePaymentGateway gateway;
    private InMemoryFakeUserRepository userRepo;
    private InMemoryFakePointHistoryRepository pointHistoryRepo;
    private UserApplicationService userService;
    private PaymentApplicationService service;
    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        paymentRepo = new InMemoryFakePaymentRepository();
        gateway = new FakePaymentGateway();
        userRepo = new InMemoryFakeUserRepository();
        pointHistoryRepo = new InMemoryFakePointHistoryRepository();
        userService = new UserApplicationService(userRepo);
        PointApplicationService pointSvc = new PointApplicationService(userService, pointHistoryRepo);
        TossProperties tossProps = new TossProperties(CLIENT_KEY, "test_sk_secret", null, null, null);
        service = new PaymentApplicationService(
                paymentRepo, gateway, pointSvc, userService,
                tossProps,
                new InMemoryFakeWebhookEventRepository(),
                new TossWebhookSignatureVerifier(tossProps),
                new ObjectMapper()
        );
        userId = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "kakao-1", new Email("u1@x.com"), "u1", null
        )).getId();
        otherUserId = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "kakao-2", new Email("u2@x.com"), "u2", null
        )).getId();
    }

    @Test
    @DisplayName("startCharge 정상_Payment 저장 + 토스 client key 반환")
    void startCharge_정상() {
        ChargeStartResult r = service.startCharge(new ChargeStartCommand(userId, 50_000L));

        assertThat(r.paymentId()).isNotNull();
        assertThat(r.merchantUid()).startsWith("charge-");
        assertThat(r.amount()).isEqualTo(50_000L);
        assertThat(r.tossClientKey()).isEqualTo(CLIENT_KEY);

        // Payment 저장됨, status=대기
        PaymentResult saved = service.getById(r.paymentId(), userId);
        assertThat(saved.status()).isEqualTo(PaymentStatus.대기);
    }

    @Test
    @DisplayName("startCharge amount <= 0_INVALID_REQUEST")
    void startCharge_invalid_amount() {
        assertThatThrownBy(() -> service.startCharge(new ChargeStartCommand(userId, 0L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.startCharge(new ChargeStartCommand(userId, -1L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("confirmCharge 정상_status=완료 + point_balance 증가")
    void confirmCharge_정상() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));

        PaymentResult r = service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        ));

        assertThat(r.status()).isEqualTo(PaymentStatus.완료);
        assertThat(gateway.confirmCalls).isEqualTo(1);
        // point_balance 증가
        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("confirmCharge 외부인_FORBIDDEN_토스 호출 X")
    void confirmCharge_외부인() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                otherUserId, "toss-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(gateway.confirmCalls).isZero();
    }

    @Test
    @DisplayName("confirmCharge amount 위변조_PAYMENT_AMOUNT_MISMATCH_토스 호출 X")
    void confirmCharge_amount_mismatch() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));

        // 클라가 보낸 amount 가 저장 amount 와 다름
        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-key", started.merchantUid(), 100_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(gateway.confirmCalls).isZero();
    }

    @Test
    @DisplayName("confirmCharge 토스 응답 orderId 위변조_PAYMENT_VERIFY_FAILED_point 미적립")
    void confirmCharge_toss_response_orderId_위변조() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        gateway.confirmOrderIdOverride = "charge-attacker-order";  // 토스가 다른 orderId echo

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);

        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isZero();
    }

    @Test
    @DisplayName("confirmCharge 토스 응답 amount 불일치_PAYMENT_AMOUNT_MISMATCH_point 미적립")
    void confirmCharge_toss_response_mismatch() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        gateway.amountOverride = 99_999L;  // 토스가 다른 amount 응답

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isZero();
    }

    @Test
    @DisplayName("confirmCharge 멱등_이미 완료 상태_토스 호출 X + 추가 적립 X")
    void confirmCharge_멱등() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        service.confirmCharge(new ChargeConfirmCommand(userId, "toss-key", started.merchantUid(), 50_000L));
        long balanceAfterFirst = userRepo.findById(userId).orElseThrow().getPointBalance();
        int callsAfterFirst = gateway.confirmCalls;

        // 같은 confirm 두 번째 호출
        PaymentResult r = service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-key", started.merchantUid(), 50_000L
        ));

        assertThat(r.status()).isEqualTo(PaymentStatus.완료);
        // 토스 호출 X (이미 완료 상태 보고 스킵)
        assertThat(gateway.confirmCalls).isEqualTo(callsAfterFirst);
        // point_balance 추가 증가 X
        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isEqualTo(balanceAfterFirst);
    }

    @Test
    @DisplayName("confirmCharge 없는 merchantUid_PAYMENT_NOT_FOUND")
    void confirmCharge_없는_uid() {
        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-key", "nonexistent-uid", 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("getById 외부인_FORBIDDEN")
    void getById_외부인() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));

        assertThatThrownBy(() -> service.getById(started.paymentId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("handleWebhook transmission-id 누락_PAYMENT_WEBHOOK_PAYLOAD_INVALID")
    void handleWebhook_transmissionId_누락() {
        assertThatThrownBy(() -> service.handleWebhook(
                "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{}}", null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
    }

    @Test
    @DisplayName("handleWebhook 동일 transmission-id 두 번_두번째는 멱등 (예외 X, lookup 한 번만)")
    void handleWebhook_멱등() {
        long amount = 5_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));
        gateway.lookupAmountOverride = amount;
        gateway.lookupOrderIdOverride = started.merchantUid();

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-dup\",\"orderId\":\"" + started.merchantUid() + "\"}}";
        service.handleWebhook(payload, "tx-dup-1");
        int lookupAfterFirst = gateway.lookupCalls;
        // 두 번째 호출 — UNIQUE 충돌 잡고 정상 종료, lookup 추가 호출 X (멱등)
        service.handleWebhook(payload, "tx-dup-1");
        assertThat(gateway.lookupCalls).isEqualTo(lookupAfterFirst);
    }

    @Test
    @DisplayName("handleWebhook PAYMENT_STATUS_CHANGED_lookup 호출 → markAsPaid + 포인트 적립")
    void handleWebhook_done_동기화() {
        long amount = 30_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));

        // FakePaymentGateway.lookup 은 lookupAmountOverride/lookupOrderIdOverride 로 응답 stub
        gateway.lookupAmountOverride = amount;
        gateway.lookupOrderIdOverride = started.merchantUid();

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"toss-pk-1\",\"orderId\":\"" + started.merchantUid() + "\"}}";
        service.handleWebhook(payload, "tx-done-1");

        // lookup 호출됐는지
        assertThat(gateway.lookupCalls).isPositive();
        // 잔액 적립 확인
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(amount);
        var p = paymentRepo.findById(started.paymentId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.완료);
    }

    @Test
    @DisplayName("handleWebhook 이미 완료된 Payment_멱등 (lookup 호출 했어도 잔액 추가 적립 X)")
    void handleWebhook_이미완료() {
        long amount = 20_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));
        // 먼저 confirm 흐름으로 완료
        service.confirmCharge(new ChargeConfirmCommand(userId, "pk-x", started.merchantUid(), amount));
        long balanceAfterConfirm = userRepo.findPointBalance(userId);

        gateway.lookupAmountOverride = amount;
        gateway.lookupOrderIdOverride = started.merchantUid();

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-x\",\"orderId\":\"" + started.merchantUid() + "\"}}";
        service.handleWebhook(payload, "tx-after-confirm");

        // 잔액 변동 없음 — 두 번 적립되면 안 됨
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(balanceAfterConfirm);
    }

    @Test
    @DisplayName("handleWebhook lookup amount 위변조_PAYMENT_AMOUNT_MISMATCH (잔액 적립 X)")
    void handleWebhook_amount_위변조() {
        long realAmount = 10_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, realAmount));

        // 토스 lookup 은 정상 DONE 인데 우리 저장 amount 와 다른 금액 (위변조 시뮬)
        gateway.lookupAmountOverride = realAmount + 50_000L;
        gateway.lookupOrderIdOverride = started.merchantUid();

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-t\",\"orderId\":\"" + started.merchantUid() + "\"}}";

        assertThatThrownBy(() -> service.handleWebhook(payload, "tx-tamper-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        // 잔액 적립 X
        assertThat(userRepo.findPointBalance(userId)).isZero();
    }

    @Test
    @DisplayName("handleWebhook eventType 없음_PAYMENT_WEBHOOK_PAYLOAD_INVALID")
    void handleWebhook_eventType_누락() {
        assertThatThrownBy(() -> service.handleWebhook("{\"data\":{}}", "tx-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
    }

    @Test
    @DisplayName("handleWebhook payload orderId 가 우리 DB 에 없음_사전 차단 (저장 X + lookup X)")
    void handleWebhook_unknown_orderId() {
        int lookupBefore = gateway.lookupCalls;
        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-z\",\"orderId\":\"unknown-merchant-uid\"}}";

        // 예외 없이 통과 (다른 가맹점 결제일 수 있음). lookup outbound X.
        service.handleWebhook(payload, "tx-unknown");
        assertThat(gateway.lookupCalls).isEqualTo(lookupBefore);
        assertThat(userRepo.findPointBalance(userId)).isZero();
    }

    @Test
    @DisplayName("handleWebhook lookup 자체가 BusinessException_무시 (트랜잭션 롤백 X, 잔액 변동 X)")
    void handleWebhook_lookup_실패() {
        long amount = 10_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));

        gateway.lookupException = new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-fail\",\"orderId\":\"" + started.merchantUid() + "\"}}";

        // 예외 없이 200 응답 — 토스 재시도 시 다시 시도 가능 (transmission-id 다르면)
        service.handleWebhook(payload, "tx-lookup-fail");
        assertThat(userRepo.findPointBalance(userId)).isZero();
    }

    @Test
    @DisplayName("handleWebhook 결제 외 eventType_DoS 차단_저장 X + lookup X (게이트 1 round 2)")
    void handleWebhook_other_eventType() {
        int lookupBefore = gateway.lookupCalls;
        service.handleWebhook("{\"eventType\":\"PAYOUT_CHANGED\",\"data\":{}}", "tx-payout-1");
        assertThat(gateway.lookupCalls).isEqualTo(lookupBefore);
        // 같은 transmission-id 두 번째 호출도 통과 (저장 안 했으니 충돌 X)
        service.handleWebhook("{\"eventType\":\"PAYOUT_CHANGED\",\"data\":{}}", "tx-payout-1");
    }

    @Test
    @DisplayName("handleWebhook PAYMENT_STATUS_CHANGED 인데 우리 결제 아님_DoS 차단 (저장 X + lookup X)")
    void handleWebhook_dos_unknown_payment() {
        int lookupBefore = gateway.lookupCalls;
        // 우리 DB 에 없는 orderId 로 webhook
        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-attacker\",\"orderId\":\"unknown-merchant\"}}";

        service.handleWebhook(payload, "tx-attack-1");

        // lookup outbound 호출 X (Toss API 부하 차단)
        assertThat(gateway.lookupCalls).isEqualTo(lookupBefore);
        // 같은 transmission-id 두 번째도 통과 (저장 안 했으니 UNIQUE 충돌 X)
        service.handleWebhook(payload, "tx-attack-1");
    }

    @Test
    @DisplayName("handleWebhook payload paymentKey/orderId 누락_PAYMENT_WEBHOOK_PAYLOAD_INVALID")
    void handleWebhook_missing_paymentKey() {
        assertThatThrownBy(() -> service.handleWebhook(
                "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{}}", "tx-x"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
    }

    @Test
    @DisplayName("handleWebhook lookup ExternalApiException_5xx 던짐 (토스 retry 트리거)")
    void handleWebhook_lookup_external_failure() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 10_000L));
        gateway.lookupException = new com.sseulang.global.exception.ExternalApiException("toss", "5xx");

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-ext\",\"orderId\":\"" + started.merchantUid() + "\"}}";

        assertThatThrownBy(() -> service.handleWebhook(payload, "tx-ext-1"))
                .isInstanceOf(com.sseulang.global.exception.ExternalApiException.class);
        // 잔액 변동 X
        assertThat(userRepo.findPointBalance(userId)).isZero();
    }

    @Test
    @DisplayName("handleWebhook 본인 pending 으로 random paymentKey 반복_DoS 가드 (이미 종결된 결제는 무시)")
    void handleWebhook_dos_against_own_pending() {
        long amount = 5_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));

        // 1차: 정상 완료
        gateway.lookupAmountOverride = amount;
        gateway.lookupOrderIdOverride = started.merchantUid();
        String payload1 = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-real\",\"orderId\":\"" + started.merchantUid() + "\"}}";
        service.handleWebhook(payload1, "tx-real-1");
        int lookupAfterReal = gateway.lookupCalls;
        assertThat(paymentRepo.findById(started.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.완료);

        // 2차 공격: 같은 orderId + random paymentKey + new transmission-id 반복
        String payload2 = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-attack\",\"orderId\":\"" + started.merchantUid() + "\"}}";
        for (int i = 0; i < 3; i++) {
            service.handleWebhook(payload2, "tx-attack-" + i);
        }

        // lookup outbound 추가 호출 X — 사전 status=완료 가드로 차단
        assertThat(gateway.lookupCalls).isEqualTo(lookupAfterReal);
    }

    @Test
    @DisplayName("handleWebhook lookup orderId 가 payload orderId 와 다름_처리 거부 (잔액 변동 X)")
    void handleWebhook_lookup_orderId_mismatch() {
        long amount = 7_000L;
        ChargeStartResult myPending = service.startCharge(new ChargeStartCommand(userId, amount));
        // 다른 사용자 pending 결제 (lookup 이 이걸 가리키도록)
        ChargeStartResult othersPending = service.startCharge(new ChargeStartCommand(otherUserId, amount));

        // payload 는 내 orderId 로 보내지만 lookup 응답은 다른 사람 orderId 로 (토스 측 비정상 응답 시뮬)
        gateway.lookupAmountOverride = amount;
        gateway.lookupOrderIdOverride = othersPending.merchantUid();

        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-mismatch\",\"orderId\":\"" + myPending.merchantUid() + "\"}}";
        service.handleWebhook(payload, "tx-mismatch-1");

        // 둘 다 잔액 변동 X
        assertThat(userRepo.findPointBalance(userId)).isZero();
        assertThat(userRepo.findPointBalance(otherUserId)).isZero();
        // 내 pending / 다른 사람 pending 모두 그대로
        assertThat(paymentRepo.findById(myPending.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.대기);
        assertThat(paymentRepo.findById(othersPending.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.대기);
    }

    @Test
    @DisplayName("handleWebhook 저장된 paymentKey 는 lookup 응답의 것 (payload 위변조 차단)")
    void handleWebhook_paymentKey_from_lookup() {
        long amount = 15_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));
        gateway.lookupAmountOverride = amount;
        gateway.lookupOrderIdOverride = started.merchantUid();
        // payload 의 paymentKey 와 다른 신뢰원 — TossPaymentGateway 가 lookup 시 받은 paymentKey 그대로 echo
        String trustworthyPaymentKey = "pk-from-lookup";

        // FakePaymentGateway 는 lookup 시 받은 paymentKey 그대로 echo 하므로 payload paymentKey 와 동일
        String payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"" + trustworthyPaymentKey + "\",\"orderId\":\"" + started.merchantUid() + "\"}}";
        service.handleWebhook(payload, "tx-pk-1");

        var p = paymentRepo.findById(started.paymentId()).orElseThrow();
        assertThat(p.getPaymentKey()).isEqualTo(trustworthyPaymentKey);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.완료);
    }

    // ───────── Codex 게이트 1 ④ — confirm ALREADY_PROCESSED → lookup 동기화 ─────────

    @Test
    @DisplayName("confirmCharge 토스 ALREADY_PROCESSED_lookup 동기화_status=완료 + 적립")
    void confirmCharge_alreadyProcessed_lookup_복구() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        // 토스 confirm 이 ALREADY_PROCESSED 로 응답하는 시나리오
        gateway.confirmException = new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        // lookup 응답 amount 는 우리 저장 amount 와 동일 (정상 복구)
        gateway.lookupAmountOverride = 50_000L;

        PaymentResult r = service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        ));

        assertThat(r.status()).isEqualTo(PaymentStatus.완료);
        assertThat(gateway.confirmCalls).isEqualTo(1);
        assertThat(gateway.lookupCalls).isEqualTo(1);
        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("confirmCharge ALREADY_PROCESSED_lookup amount 위변조_PAYMENT_AMOUNT_MISMATCH_적립 X")
    void confirmCharge_alreadyProcessed_lookup_위변조() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        gateway.confirmException = new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        gateway.lookupAmountOverride = 99_999L;  // 위변조 — lookup 결과 amount 가 다름

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isZero();
    }

    @Test
    @DisplayName("confirmCharge ALREADY_PROCESSED_lookup orderId 위변조_PAYMENT_VERIFY_FAILED_적립 X")
    void confirmCharge_alreadyProcessed_lookup_orderId_위변조() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        gateway.confirmException = new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        // lookup 응답이 다른 결제건의 orderId 를 echo (paymentKey 만 같고 우리 결제 아님)
        gateway.lookupOrderIdOverride = "charge-attacker-order";

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);

        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isZero();
    }

    @Test
    @DisplayName("confirmCharge ALREADY_PROCESSED_lookup 자체가 BusinessException 던짐_그대로 전파")
    void confirmCharge_alreadyProcessed_lookup_failure_전파() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        gateway.confirmException = new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        gateway.lookupException = new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);

        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isZero();
    }

    @Test
    @DisplayName("confirmCharge ALREADY_PROCESSED 외 다른 BusinessException_그대로 전파_lookup 호출 X")
    void confirmCharge_other_business_exception_전파() {
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, 50_000L));
        gateway.confirmException = new BusinessException(ErrorCode.PAYMENT_VERIFY_FAILED);

        assertThatThrownBy(() -> service.confirmCharge(new ChargeConfirmCommand(
                userId, "toss-payment-key", started.merchantUid(), 50_000L
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_VERIFY_FAILED);

        assertThat(gateway.lookupCalls).isZero();
        assertThat(userRepo.findById(userId).orElseThrow().getPointBalance()).isZero();
    }
}
