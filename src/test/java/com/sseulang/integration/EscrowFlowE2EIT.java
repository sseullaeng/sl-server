package com.sseulang.integration;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.escrow.application.EscrowApplicationService;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateCommand;
import com.sseulang.domain.escrow.application.dto.EscrowApplicationResult;
import com.sseulang.domain.escrow.application.dto.EscrowLinkCreateCommand;
import com.sseulang.domain.escrow.application.dto.EscrowLinkResult;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import com.sseulang.domain.payment.application.PaymentApplicationService;
import com.sseulang.domain.payment.application.dto.ChargeConfirmCommand;
import com.sseulang.domain.payment.application.dto.ChargeStartCommand;
import com.sseulang.domain.payment.application.dto.ChargeStartResult;
import com.sseulang.domain.payment.domain.PaymentConfirmResult;
import com.sseulang.domain.payment.domain.PaymentGateway;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 거래대행 e2e — link 생성 → 폼 제출 → 양쪽 결제 → Delivery 자동 매칭 → 라이더 수락 → markDelivered
 * → Mode A 자동 정산 / Mode B 수령 확인 정산. PaymentGateway 만 mock — DB·Redis·Mongo·이벤트 모두 real.
 */
@SpringBootTest
@Testcontainers
class EscrowFlowE2EIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("sseulang").withUsername("test").withPassword("test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                            "--ngram_token_size=2");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        r.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        r.add("app.dev-auth.enabled", () -> "false");
    }

    @Autowired private EscrowApplicationService escrowService;
    @Autowired private DeliveryApplicationService deliveryService;
    @Autowired private PaymentApplicationService paymentService;
    @Autowired private UserApplicationService userService;
    @Autowired private com.sseulang.domain.escrow.domain.EscrowFeeSettingsRepository feeSettingsRepository;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    @MockBean
    private PaymentGateway paymentGateway;

    private Long buyerId;
    private Long sellerId;
    private Long riderId;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(s -> {
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM deliveries").executeUpdate();
            em.createNativeQuery("DELETE FROM payments").executeUpdate();
            em.createNativeQuery("DELETE FROM escrow_applications").executeUpdate();
            em.createNativeQuery("DELETE FROM escrow_links").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
        // 3명 가입 + verified
        buyerId = createVerifiedUser("buyer");
        sellerId = createVerifiedUser("seller");
        riderId = createVerifiedUser("rider");
        // rider 권한 부여
        tx.execute(s -> {
            em.createNativeQuery("UPDATE users SET is_rider = TRUE WHERE id = :id")
                    .setParameter("id", riderId).executeUpdate();
            return null;
        });
    }

    private Long createVerifiedUser(String suffix) {
        // social 가입 후 인증 수동 처리.
        User created = userService.findOrCreateBySocial(
                SocialProvider.KAKAO, "social-" + suffix + "-" + UUID.randomUUID(),
                new Email(suffix + "@x.com"), suffix, null
        );
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("UPDATE users SET email_verified = TRUE WHERE id = :id")
                    .setParameter("id", created.getId()).executeUpdate();
            return null;
        });
        return created.getId();
    }

    private long balance(Long userId) {
        return ((Number) em.createNativeQuery("SELECT point_balance FROM users WHERE id = :id")
                .setParameter("id", userId).getSingleResult()).longValue();
    }

    /** 양쪽 결제 (escrow application 의 share 양쪽) — paymentGateway mock 으로 confirm. */
    private void payShare(Long payerId, Long applicationId, long amount) {
        // start
        ChargeStartResult started = paymentService.startCharge(
                new ChargeStartCommand(payerId, amount, applicationId)
        );
        // toss confirm mock
        when(paymentGateway.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(new PaymentConfirmResult(
                        "key-" + started.merchantUid(),
                        started.merchantUid(),
                        amount,
                        PaymentMethod.CARD,
                        LocalDateTime.now(),
                        "{}"
                ));
        // confirm
        paymentService.confirmCharge(new ChargeConfirmCommand(
                payerId, "key-" + started.merchantUid(), started.merchantUid(), amount
        ));
    }

    @Test
    @DisplayName("Mode B INTERNAL feePayer=buyer e2e — buyer 잔액 변동 X + seller 정산 + rider 정산")
    void mode_b_internal_full_flow() {
        // 1) link 생성 (buyer initiator, feePayer=buyer)
        EscrowLinkResult link = escrowService.createLink(EscrowLinkCreateCommand.legacy(
                buyerId, InitiatorRole.buyer, FeePayer.buyer, TradeMode.INTERNAL
        ));

        // 2) 수신자 (seller) 폼 제출 — itemPrice=1M
        long itemPrice = 1_000_000L;
        EscrowApplicationCreateCommand cmd = formCommand(sellerId, link.linkToken(), itemPrice);
        EscrowApplicationResult app = escrowService.createApplication(cmd);
        assertThat(app.status()).isEqualTo(EscrowApplicationStatus.결제대기);
        assertThat(app.buyerId()).isEqualTo(buyerId);
        assertThat(app.sellerId()).isEqualTo(sellerId);
        // feePayer=buyer → seller share = 0, buyer share = item + fees
        assertThat(app.receiverShare()).isZero();
        assertThat(app.initiatorShare()).isEqualTo(app.appliedTotalFee());

        // 3) buyer 결제 (initiator = buyer)
        payShare(buyerId, app.id(), app.initiatorShare());

        // 4) status = 결제완료 + delivery 모집중 row 자동 생성 (event listener)
        EscrowApplicationResult afterPay = escrowService.getById(app.id(), buyerId);
        assertThat(afterPay.status()).isEqualTo(EscrowApplicationStatus.결제완료);
        Number deliveryCount = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM deliveries WHERE escrow_application_id = :id"
        ).setParameter("id", app.id()).getSingleResult();
        assertThat(deliveryCount.intValue()).isEqualTo(1);

        // 5) rider 가 delivery 수락 + pickup + delivered
        Long deliveryId = ((Number) em.createNativeQuery(
                "SELECT id FROM deliveries WHERE escrow_application_id = :id"
        ).setParameter("id", app.id()).getSingleResult()).longValue();
        deliveryService.accept(deliveryId, riderId);
        deliveryService.markPickedUp(deliveryId, riderId);
        deliveryService.markDelivered(deliveryId, riderId);

        // 6) Mode B 라 자동 정산 X (settleAfterDelivery 가 INTERNAL 분기 return 하므로 진행중 유지)
        // application 상태는 결제완료 그대로 (markInProgress 호출 안 했음 — 5/11 시점 단순화).
        // Mode B 정산은 buyer 의 confirmReceipt 호출.
        // 단 markInProgress 가 없어서 confirmReceipt 가 결제완료 → INVALID_STATE 던짐.
        // → 진행중 마커가 명시되어야 함. ApplicationService.markInProgress 호출.
        escrowService.markInProgress(app.id());

        // 7) buyer 수령 확인 → 정산
        escrowService.confirmReceipt(app.id(), buyerId);

        // 8) 잔액 검증
        EscrowApplicationResult finalApp = escrowService.getById(app.id(), buyerId);
        assertThat(finalApp.status()).isEqualTo(EscrowApplicationStatus.완료);
        assertThat(finalApp.settledAt()).isNotNull();
        // buyer: 결제 X (escrow hold — 잔액 변동 0)
        assertThat(balance(buyerId)).isZero();
        // seller: itemPrice 적립
        assertThat(balance(sellerId)).isEqualTo(itemPrice);
        // rider: deliveryFee 적립 (snapshot 값)
        assertThat(balance(riderId)).isEqualTo(finalApp.appliedDeliveryFee());

        // B-5: settle 시 paired Transaction 자동 생성 검증 (commit 58f2d19).
        // 어드민/리뷰가 직거래와 동일 모델로 접근 가능해야 함.
        Number txCount = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM transactions WHERE escrow_application_id = :id"
        ).setParameter("id", app.id()).getSingleResult();
        assertThat(txCount.intValue()).as("paired Transaction 1건 자동 생성").isEqualTo(1);

        Object[] txRow = (Object[]) em.createNativeQuery(
                "SELECT status, seller_id, buyer_id, price, escrow_application_id "
                        + "FROM transactions WHERE escrow_application_id = :id"
        ).setParameter("id", app.id()).getSingleResult();
        assertThat(txRow[0]).as("status=거래완료").isEqualTo("거래완료");
        assertThat(((Number) txRow[1]).longValue()).isEqualTo(sellerId);
        assertThat(((Number) txRow[2]).longValue()).isEqualTo(buyerId);
        assertThat(((Number) txRow[3]).longValue()).isEqualTo(itemPrice);
        assertThat(((Number) txRow[4]).longValue()).isEqualTo(app.id());
    }

    @Test
    @DisplayName("createApplication race — 두 수신자 중 한 명만 성공")
    void claim_race() {
        EscrowLinkResult link = escrowService.createLink(EscrowLinkCreateCommand.legacy(
                buyerId, InitiatorRole.buyer, FeePayer.buyer, TradeMode.INTERNAL
        ));
        // seller 가 첫 폼 제출
        escrowService.createApplication(formCommand(sellerId, link.linkToken(), 500_000L));

        // rider 가 같은 link 에 시도 → ALREADY_TAKEN
        assertThatThrownBy(() ->
                escrowService.createApplication(formCommand(riderId, link.linkToken(), 500_000L))
        ).isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ESCROW_LINK_ALREADY_TAKEN));
    }

    private EscrowApplicationCreateCommand formCommand(Long receiverId, String linkToken, long itemPrice) {
        BigDecimal pickLat = new BigDecimal("37.5065");
        BigDecimal pickLng = new BigDecimal("127.0530");
        BigDecimal dropLat = new BigDecimal("37.5144");
        BigDecimal dropLng = new BigDecimal("127.1058");
        BigDecimal dist = com.sseulang.domain.escrow.domain.EscrowFeeCalculator.distanceKm(
                pickLat.doubleValue(), pickLng.doubleValue(),
                dropLat.doubleValue(), dropLng.doubleValue()
        );
        // 백엔드가 form 의 좌표로 동일하게 재계산 — 같은 calculator 사용해서 일치하는 fee 미리 산정.
        var feeSettings = feeSettingsRepository.findSingleton();
        var fb = com.sseulang.domain.escrow.domain.EscrowFeeCalculator.calculate(
                feeSettings,
                itemPrice > 0 ? TradeMode.INTERNAL : TradeMode.EXTERNAL,
                itemPrice, dist,
                Weight.R1TO3, Volume.M, Fragility.F3
        );
        return new EscrowApplicationCreateCommand(
                receiverId, linkToken,
                itemPrice, "테스트 물품",
                "픽업주소", pickLat, pickLng,
                "도착주소", dropLat, dropLng,
                Weight.R1TO3, Volume.M, Fragility.F3, "메모",
                fb.deliveryFee(), fb.commissionFee(), fb.totalFee(), dist,
                java.util.List.of()
        );
    }
}
