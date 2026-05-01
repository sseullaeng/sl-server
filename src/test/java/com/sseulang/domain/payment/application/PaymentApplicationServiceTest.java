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
    @DisplayName("handleWebhook payload 파싱 + WebhookEvent 저장 (시그니처 검증 비활성)")
    void handleWebhook() {
        // webhookSecret null 이라 시그니처 검증 비활성. eventId 만 있으면 저장 성공.
        service.handleWebhook(
                "{\"eventId\":\"evt-1\",\"eventType\":\"PAYMENT.STATUS_CHANGED\",\"data\":{}}",
                null, null
        );
    }

    @Test
    @DisplayName("handleWebhook 동일 eventId 두 번_두번째는 멱등 (예외 X, 잔액 변동 X)")
    void handleWebhook_멱등() {
        String payload = "{\"eventId\":\"evt-dup\",\"eventType\":\"PAYMENT.STATUS_CHANGED\",\"data\":{}}";
        service.handleWebhook(payload, null, null);
        // 두 번째 호출 — UNIQUE 충돌 잡고 정상 종료해야 함
        service.handleWebhook(payload, null, null);
    }

    @Test
    @DisplayName("handleWebhook PAYMENT.STATUS_CHANGED + DONE_Payment markAsPaid + 포인트 적립")
    void handleWebhook_done_동기화() {
        long amount = 30_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));

        String payload = String.format(
                "{\"eventId\":\"evt-done-1\",\"eventType\":\"PAYMENT.STATUS_CHANGED\"," +
                        "\"data\":{\"paymentKey\":\"pk-1\",\"orderId\":\"%s\",\"status\":\"DONE\",\"totalAmount\":%d}}",
                started.merchantUid(), amount
        );
        service.handleWebhook(payload, null, null);

        // 잔액 적립 확인
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(amount);
        // 결제 상태
        var p = paymentRepo.findById(started.paymentId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.완료);
    }

    @Test
    @DisplayName("handleWebhook 이미 완료된 Payment_멱등 (잔액 추가 적립 X)")
    void handleWebhook_이미완료() {
        long amount = 20_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, amount));
        // 먼저 confirm 흐름으로 완료 — fake gateway 가 amount/orderId echo
        service.confirmCharge(new ChargeConfirmCommand(userId, "pk-x", started.merchantUid(), amount));
        long balanceAfterConfirm = userRepo.findPointBalance(userId);

        // 그 다음 webhook 으로 동일 결제 통보
        String payload = String.format(
                "{\"eventId\":\"evt-after-confirm\",\"eventType\":\"PAYMENT.STATUS_CHANGED\"," +
                        "\"data\":{\"paymentKey\":\"pk-x\",\"orderId\":\"%s\",\"status\":\"DONE\",\"totalAmount\":%d}}",
                started.merchantUid(), amount
        );
        service.handleWebhook(payload, null, null);

        // 잔액 변동 없음 — 두 번 적립되면 안 됨
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(balanceAfterConfirm);
    }

    @Test
    @DisplayName("handleWebhook amount 위변조_PAYMENT_AMOUNT_MISMATCH")
    void handleWebhook_amount_위변조() {
        long realAmount = 10_000L;
        ChargeStartResult started = service.startCharge(new ChargeStartCommand(userId, realAmount));

        String payload = String.format(
                "{\"eventId\":\"evt-tamper\",\"eventType\":\"PAYMENT.STATUS_CHANGED\"," +
                        "\"data\":{\"paymentKey\":\"pk-t\",\"orderId\":\"%s\",\"status\":\"DONE\",\"totalAmount\":%d}}",
                started.merchantUid(), realAmount + 50_000L  // 위변조
        );

        assertThatThrownBy(() -> service.handleWebhook(payload, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        // 잔액 적립 X
        assertThat(userRepo.findPointBalance(userId)).isZero();
    }

    @Test
    @DisplayName("handleWebhook eventId 없음_PAYMENT_WEBHOOK_PAYLOAD_INVALID")
    void handleWebhook_eventId_누락() {
        assertThatThrownBy(() -> service.handleWebhook(
                "{\"eventType\":\"PAYMENT.STATUS_CHANGED\",\"data\":{}}",
                null, null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
    }

    @Test
    @DisplayName("handleWebhook 알 수 없는 orderId_무시 (예외 X, 잔액 변동 X)")
    void handleWebhook_unknown_orderId() {
        String payload = "{\"eventId\":\"evt-unknown\",\"eventType\":\"PAYMENT.STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-z\",\"orderId\":\"unknown-order\",\"status\":\"DONE\",\"totalAmount\":1000}}";

        // 예외 없이 통과해야 함 (다른 시스템 결제일 수 있음)
        service.handleWebhook(payload, null, null);
        assertThat(userRepo.findPointBalance(userId)).isZero();
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
