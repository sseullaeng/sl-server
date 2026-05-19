package com.sseulang.domain.stats.integration;

import com.sseulang.domain.payment.domain.Payment;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.domain.payment.domain.PaymentRepository;
import com.sseulang.domain.payment.infrastructure.persistence.PaymentRepositoryImpl;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.domain.user.infrastructure.persistence.UserRepositoryImpl;
import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalRepository;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatusCount;
import com.sseulang.domain.withdrawal.infrastructure.persistence.WithdrawalRepositoryImpl;
import com.sseulang.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 9 PR-b 게이트 2 — 관리자 통계 집계 쿼리 DB-backed IT.
 *
 * <p>회귀 가드 항목:
 * <ul>
 *   <li>User: countActive 가 blocked && deleted 사용자도 정확히 active 에서 제외</li>
 *   <li>Withdrawal: GROUP BY constructor projection (record 매핑)</li>
 *   <li>Withdrawal: COALESCE SUM 이 0건 상태에서도 0 반환 (NULL 회피)</li>
 *   <li>Payment: SUM(amount) WHERE status = 완료 정확</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        WithdrawalRepositoryImpl.class,
        PaymentRepositoryImpl.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminStatsAggregationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sseulang_test")
            .withUsername("test")
            .withPassword("testpw")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--ngram_token_size=2");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private EntityManager em;
    @Autowired private UserRepository userRepository;
    @Autowired private WithdrawalRepository withdrawalRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.execute(s -> {
            em.createNativeQuery("DELETE FROM withdrawals").executeUpdate();
            em.createNativeQuery("DELETE FROM payments").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("User countActive_blocked && deleted 사용자도 정확히 제외 (이중 차감 방지 회귀)")
    void user_countActive_정확() {
        // 시드: total 5, blocked-only 1, deleted-only 1, both 1, normal 2 → active 2
        Long both = tx.execute(s -> persistUser("u-both", true, true));
        Long blockedOnly = tx.execute(s -> persistUser("u-blocked", true, false));
        Long deletedOnly = tx.execute(s -> persistUser("u-deleted", false, true));
        tx.execute(s -> persistUser("u-normal-1", false, false));
        tx.execute(s -> persistUser("u-normal-2", false, false));

        assertThat(userRepository.countAll()).isEqualTo(5L);
        assertThat(userRepository.countBlocked()).as("both 포함").isEqualTo(2L);
        assertThat(userRepository.countDeleted()).as("both 포함").isEqualTo(2L);
        assertThat(userRepository.countActive())
                .as("blocked && deleted 도 active 에서 정확히 제외 — total - blocked - deleted = -1 이지만 실제 active = 2")
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("Withdrawal GROUP BY constructor projection_record 매핑 정상")
    void withdrawal_groupBy_projection() {
        Long userId = tx.execute(s -> persistUser("w-user", false, false));

        // 시드: 신청 2, 승인 1, 완료 1, 거부 1
        seedWithdrawal(userId, "k1", 10_000L, WithdrawalStatus.신청, null);
        seedWithdrawal(userId, "k2", 20_000L, WithdrawalStatus.신청, null);
        seedWithdrawal(userId, "k3", 30_000L, WithdrawalStatus.승인, null);
        seedWithdrawal(userId, "k4", 40_000L, WithdrawalStatus.완료, null);
        seedWithdrawal(userId, "k5", 50_000L, WithdrawalStatus.거부, null);

        List<WithdrawalStatusCount> rows = withdrawalRepository.countGroupByStatus();
        Map<WithdrawalStatus, Long> byStatus = rows.stream()
                .collect(Collectors.toMap(WithdrawalStatusCount::status, WithdrawalStatusCount::count));

        assertThat(byStatus)
                .containsEntry(WithdrawalStatus.신청, 2L)
                .containsEntry(WithdrawalStatus.승인, 1L)
                .containsEntry(WithdrawalStatus.완료, 1L)
                .containsEntry(WithdrawalStatus.거부, 1L);
    }

    @Test
    @DisplayName("Withdrawal sumCompletedAmount_완료만 합산 + 0건일 때 0 반환 (COALESCE)")
    void withdrawal_sum_completed() {
        // 0건 상태 — COALESCE 가드 검증
        assertThat(withdrawalRepository.sumCompletedAmount()).as("0건 → 0").isZero();

        Long userId = tx.execute(s -> persistUser("ws-user", false, false));
        seedWithdrawal(userId, "ks1", 30_000L, WithdrawalStatus.완료, null);
        seedWithdrawal(userId, "ks2", 70_000L, WithdrawalStatus.완료, null);
        seedWithdrawal(userId, "ks3", 99_999L, WithdrawalStatus.신청, null);  // 미완료 — 합산 X

        assertThat(withdrawalRepository.sumCompletedAmount())
                .as("완료된 출금만 합산 — 신청은 제외")
                .isEqualTo(100_000L);
    }

    @Test
    @DisplayName("Payment sumPaidAmount + countPaid_완료만 / 0건 → 0")
    void payment_sum_paid() {
        assertThat(paymentRepository.sumPaidAmount()).isZero();
        assertThat(paymentRepository.countPaid()).isZero();

        Long userId = tx.execute(s -> persistUser("p-user", false, false));
        // 시드: 완료 2건 (50k + 30k), 실패 1건 (99k 합산 X)
        seedPayment(userId, 50_000L, true);
        seedPayment(userId, 30_000L, true);
        seedPayment(userId, 99_999L, false);

        assertThat(paymentRepository.countPaid()).isEqualTo(2L);
        assertThat(paymentRepository.sumPaidAmount()).as("완료 결제만 합산").isEqualTo(80_000L);
    }

    // ───────── 시드 헬퍼 ─────────

    /**
     * User 시드 — createSocialUser 후 blocked/deleted 는 ReflectionTestUtils 로 강제 설정.
     * 본 PR scope 에선 block/unblock 도메인 메서드 미존재 (PR-a 에서 추가됨, dev 머지 전).
     */
    private Long persistUser(String suffix, boolean blocked, boolean deleted) {
        User u = User.createSocialUser(
                SocialProvider.KAKAO,
                "kakao-" + suffix + "-" + System.nanoTime(),
                new Email(suffix + "@stats.test"),
                "user-" + suffix,
                null
        );
        if (blocked) ReflectionTestUtils.setField(u, "blocked", true);
        if (deleted) ReflectionTestUtils.setField(u, "deleted", true);
        em.persist(u);
        em.flush();
        return u.getId();
    }

    private void seedWithdrawal(Long userId, String key, long amount, WithdrawalStatus targetStatus, Object _ignored) {
        tx.execute(s -> {
            Withdrawal w = Withdrawal.request(userId, key, amount, "신한", "110-1", "홍길동", LocalDateTime.now());
            // 상태 전이 — Aggregate 메서드는 invariant 가드가 있어 시드 시점엔 reflection 으로 직접 적용.
            ReflectionTestUtils.setField(w, "status", targetStatus);
            withdrawalRepository.save(w);
            return null;
        });
    }

    private void seedPayment(Long userId, long amount, boolean paid) {
        tx.execute(s -> {
            Payment p = Payment.startCharge(userId, "p-" + UUID.randomUUID(), amount);
            if (paid) {
                p.markAsPaid("pk-" + UUID.randomUUID(), PaymentMethod.CARD, LocalDateTime.now(), "{}");
            } else {
                p.markAsFailed("test failure");
            }
            paymentRepository.save(p);
            return null;
        });
    }
}
