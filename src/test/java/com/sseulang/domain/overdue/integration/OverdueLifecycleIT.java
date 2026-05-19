package com.sseulang.domain.overdue.integration;

import com.sseulang.domain.escrow.application.EscrowOverdueQueryService;
import com.sseulang.domain.escrow.application.dto.EscrowOverdueSnapshot;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import com.sseulang.domain.overdue.application.OverdueApplicationService;
import com.sseulang.domain.overdue.domain.OverduePhase;
import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueRecordRepository;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 연체 lifecycle e2e — 실 DB 위에서 OverdueApplicationService 동작 검증.
 * EscrowOverdueQueryService 는 mock — escrow 실 흐름 우회 (overdue 도메인 격리).
 *
 * 검증:
 *  1. startOverdue → Day 1 보증금 30% 차감 + seller 정산
 *  2. advanceDay 14일 도달 → Phase 3 자동 정지 (suspend 14일 누적)
 *  3. markResolvedByReturn → 잔여 보증금 환불
 */
@SpringBootTest
@Testcontainers
class OverdueLifecycleIT {

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

    @Autowired private OverdueApplicationService overdueService;
    @Autowired private OverdueRecordRepository overdueRecordRepository;
    @Autowired private UserApplicationService userService;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    @MockBean
    private EscrowOverdueQueryService escrowOverdueQueryService;

    private static final long DEPOSIT = 100_000L;
    private static final LocalDateTime RENTAL_END = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime DAY1 = RENTAL_END.plusHours(24);

    private Long buyerId;
    private Long sellerId;
    private Long escrowAppId;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(s -> {
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM overdue_records").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });

        buyerId = createUser("buyer");
        sellerId = createUser("seller");

        // 보증금 hold 시뮬레이션 — buyer.point_balance 채운 후 hold 로 이동.
        tx.execute(s -> {
            em.createNativeQuery("UPDATE users SET point_balance = :amt WHERE id = :id")
                    .setParameter("amt", DEPOSIT).setParameter("id", buyerId).executeUpdate();
            return null;
        });
        userService.holdForEscrow(buyerId, DEPOSIT);

        // escrow_application FK 우회 — 임의 id 사용 + mock query service 가 snapshot 반환.
        escrowAppId = 9_999L;
        when(escrowOverdueQueryService.getForOverdue(escrowAppId)).thenReturn(snapshot());
    }

    private Long createUser(String suffix) {
        User u = userService.findOrCreateBySocial(
                SocialProvider.KAKAO, "social-" + suffix + "-" + UUID.randomUUID(),
                new Email(suffix + "@x.com"), suffix, null
        );
        return u.getId();
    }

    private EscrowOverdueSnapshot snapshot() {
        return new EscrowOverdueSnapshot(
                escrowAppId, buyerId, sellerId, DEPOSIT, RENTAL_END,
                EscrowApplicationStatus.사용중, true
        );
    }

    private long balance(Long userId) {
        return ((Number) em.createNativeQuery("SELECT point_balance FROM users WHERE id = :id")
                .setParameter("id", userId).getSingleResult()).longValue();
    }

    private long hold(Long userId) {
        return ((Number) em.createNativeQuery("SELECT point_hold FROM users WHERE id = :id")
                .setParameter("id", userId).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("startOverdue_Day1_보증금_30%_차감_+_seller_정산")
    void startOverdue_day1() {
        // FK constraint 우회 — escrow_application_id 없는 record 저장 위해 임시로 FK off.
        disableFk();
        try {
            boolean started = overdueService.startOverdue(escrowAppId, DAY1);
            assertThat(started).isTrue();

            // buyer hold: 100,000 → 70,000 (30% 차감)
            assertThat(hold(buyerId)).isEqualTo(70_000L);
            assertThat(balance(buyerId)).isZero();
            // seller balance: 0 → 30,000
            assertThat(balance(sellerId)).isEqualTo(30_000L);
        } finally {
            enableFk();
        }
    }

    @Test
    @DisplayName("advanceDay_14일_도달_Phase3_자동_정지")
    void advanceDay_phase3_auto_suspend() {
        disableFk();
        try {
            overdueService.startOverdue(escrowAppId, DAY1);
            OverdueRecord record = overdueRecordRepository.findByEscrowApplicationId(escrowAppId)
                    .orElseThrow();

            // Day 14 도달 — phase3DaysThreshold(14) 또는 totalDebt 임계 어느 쪽이든 트리거
            overdueService.advanceDay(record.getId(), DAY1.plusDays(13));

            OverdueRecord refreshed = overdueRecordRepository.findById(record.getId()).orElseThrow();
            assertThat(refreshed.getOverdueDays()).isEqualTo(14);
            assertThat(refreshed.getPhase()).isEqualTo(OverduePhase.PHASE_3);
            assertThat(refreshed.getAccountSuspendedAt()).isNotNull();

            // buyer 가 자동 정지됐는지 — suspended_at not null
            Object suspendedAt = em.createNativeQuery(
                    "SELECT suspended_at FROM users WHERE id = :id"
            ).setParameter("id", buyerId).getSingleResult();
            assertThat(suspendedAt).isNotNull();
        } finally {
            enableFk();
        }
    }

    @Test
    @DisplayName("markResolvedByReturn_잔여_보증금_환불_+_상태_정산완료")
    void markResolvedByReturn() {
        disableFk();
        try {
            overdueService.startOverdue(escrowAppId, DAY1);
            // Day 1 후 hold 70,000 잔여
            assertThat(hold(buyerId)).isEqualTo(70_000L);

            overdueService.markResolvedByReturn(escrowAppId, DAY1.plusDays(1));

            // 잔여 hold (70,000) → balance 환불
            assertThat(hold(buyerId)).isZero();
            assertThat(balance(buyerId)).isEqualTo(70_000L);

            OverdueRecord record = overdueRecordRepository.findByEscrowApplicationId(escrowAppId)
                    .orElseThrow();
            // extraDebt 없음 → 종료
            assertThat(record.getStatus()).isEqualTo(OverdueStatus.종료);
        } finally {
            enableFk();
        }
    }

    private void disableFk() {
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            return null;
        });
    }

    private void enableFk() {
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
            return null;
        });
    }
}
