package com.sseulang.domain.withdrawal.integration;

import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.infrastructure.persistence.PointHistoryRepositoryImpl;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.domain.user.infrastructure.persistence.UserRepositoryImpl;
import com.sseulang.domain.withdrawal.application.WithdrawalApplicationService;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalRequestCommand;
import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalRepository;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.domain.withdrawal.infrastructure.persistence.WithdrawalRepositoryImpl;
import com.sseulang.global.config.JpaAuditingConfig;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Day 8 PR-b 게이트 1 — 출금 잔액 정합성 DB-backed IT.
 *
 * <ol>
 *   <li>{@code request 잔액 부족} — Withdrawal 행 / point_histories / 잔액 모두 원복 (트랜잭션 롤백)</li>
 *   <li>{@code cancel 잔액 원복} — 신청 차감 → 취소 시 잔액 +amount 환불 + 환불 history 적재</li>
 *   <li>{@code adminReject 잔액 원복} — 관리자 거부 시 잔액 환불</li>
 *   <li>{@code adminComplete} — 외부 이체 mock 후 status=완료, 잔액 변동 없음</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserApplicationService.class,
        PointHistoryRepositoryImpl.class,
        PointApplicationService.class,
        WithdrawalRepositoryImpl.class,
        WithdrawalApplicationService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawalSettlementIT {

    private long adminId;

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
    @Autowired private WithdrawalApplicationService withdrawalService;
    @Autowired private WithdrawalRepository withdrawalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;
    private Long userId;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(s -> {
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM withdrawals").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            em.createNativeQuery("DELETE FROM admins").executeUpdate();
            return null;
        });
        userId = txTemplate.execute(s -> persistUser());
        adminId = txTemplate.execute(s -> persistAdmin());
        txTemplate.execute(s -> {
            userRepository.creditPointBalance(userId, 100_000L);
            return null;
        });
    }

    @Test
    @DisplayName("request 잔액 부족_Withdrawal/point_histories/잔액 모두 롤백")
    void request_잔액부족_롤백() {
        assertThatThrownBy(() -> txTemplate.execute(s -> {
            withdrawalService.request(new WithdrawalRequestCommand(
                    userId, idemKey(), 200_000L, "신한", "110-123-456789", "홍길동"
            ));
            return null;
        }))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        // verify — Withdrawal 행 미생성 (트랜잭션 롤백) / point_history 미적재 / 잔액 변동 X
        assertThat(withdrawalRepository.findByStatus(null,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .as("Withdrawal 행 롤백").isEmpty();
        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .as("history 미적재").isEmpty();
        assertThat(userRepository.findPointBalance(userId)).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("cancel 정상_잔액 원복 + 출금/환불 history 두 건")
    void cancel_정상() {
        Long id = txTemplate.execute(s -> withdrawalService.request(new WithdrawalRequestCommand(
                userId, idemKey(), 50_000L, "신한", "110-123-456789", "홍길동"
        )));
        // 신청 직후 잔액 50000
        assertThat(userRepository.findPointBalance(userId)).isEqualTo(50_000L);

        txTemplate.execute(s -> {
            withdrawalService.cancel(id, userId);
            return null;
        });

        Withdrawal w = withdrawalRepository.findById(id).orElseThrow();
        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.취소);
        assertThat(userRepository.findPointBalance(userId)).isEqualTo(100_000L);  // 원복
        var histories = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(histories).hasSize(2);
        // 같은 트랜잭션 안에서 생성되어 created_at 동일할 수 있음 — 매칭 기반 검증.
        PointHistory withdrawHistory = histories.stream()
                .filter(h -> h.getPointType() == PointHistoryType.출금).findFirst().orElseThrow();
        PointHistory refundHistory = histories.stream()
                .filter(h -> h.getPointType() == PointHistoryType.환불).findFirst().orElseThrow();
        assertThat(withdrawHistory.getAmount()).isEqualTo(-50_000L);
        assertThat(withdrawHistory.getBalanceAfter()).isEqualTo(50_000L);
        assertThat(refundHistory.getAmount()).isEqualTo(50_000L);
        assertThat(refundHistory.getBalanceAfter()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("adminReject 정상_잔액 원복")
    void adminReject_정상() {
        Long id = txTemplate.execute(s -> withdrawalService.request(new WithdrawalRequestCommand(
                userId, idemKey(), 30_000L, "신한", "110-123-456789", "홍길동"
        )));

        txTemplate.execute(s -> {
            withdrawalService.adminReject(id, adminId, "계좌 검증 실패");
            return null;
        });

        assertThat(withdrawalRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(WithdrawalStatus.거부);
        assertThat(userRepository.findPointBalance(userId)).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("동시 cancel vs adminApprove_정확히 1건만 처리 + 다른 1건은 상태 가드로 거부")
    void 동시_cancel_vs_adminApprove() throws Exception {
        Long id = txTemplate.execute(s -> withdrawalService.request(new WithdrawalRequestCommand(
                userId, idemKey(), 30_000L, "신한", "110-123-456789", "홍길동"
        )));

        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger cancelOk = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger approveOk = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger stateErr = new java.util.concurrent.atomic.AtomicInteger();

        exec.submit(() -> {
            try {
                start.await();
                txTemplate.execute(s -> { withdrawalService.cancel(id, userId); return null; });
                cancelOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.WITHDRAWAL_NOT_CANCELABLE
                        || e.getErrorCode() == ErrorCode.WITHDRAWAL_INVALID_STATE) {
                    stateErr.incrementAndGet();
                }
            } catch (Exception ignore) {
            } finally { done.countDown(); }
        });
        exec.submit(() -> {
            try {
                start.await();
                txTemplate.execute(s -> { withdrawalService.adminApprove(id, adminId, "ok"); return null; });
                approveOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.WITHDRAWAL_NOT_CANCELABLE
                        || e.getErrorCode() == ErrorCode.WITHDRAWAL_INVALID_STATE) {
                    stateErr.incrementAndGet();
                }
            } catch (Exception ignore) {
            } finally { done.countDown(); }
        });
        start.countDown();
        boolean finished = done.await(10, java.util.concurrent.TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 호출 모두 10초 안에 완료").isTrue();
        // 두 흐름 중 정확히 1개 성공, 다른 1개는 상태 가드로 거부 (락 직렬화).
        assertThat(cancelOk.get() + approveOk.get()).as("성공 1건").isEqualTo(1);
        assertThat(stateErr.get()).as("거부 1건").isEqualTo(1);

        Withdrawal w = withdrawalRepository.findById(id).orElseThrow();
        assertThat(w.getStatus()).isIn(WithdrawalStatus.취소, WithdrawalStatus.승인);
    }

    @Test
    @DisplayName("adminApprove → adminComplete_status=완료, 잔액 변동 없음")
    void adminApproveComplete() {
        Long id = txTemplate.execute(s -> withdrawalService.request(new WithdrawalRequestCommand(
                userId, idemKey(), 30_000L, "신한", "110-123-456789", "홍길동"
        )));

        txTemplate.execute(s -> { withdrawalService.adminApprove(id, adminId, "ok"); return null; });
        txTemplate.execute(s -> { withdrawalService.adminComplete(id, adminId); return null; });

        Withdrawal w = withdrawalRepository.findById(id).orElseThrow();
        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.완료);
        assertThat(w.getAdminId()).isEqualTo(adminId);
        // 신청 시점 차감만 유지 (외부 이체 mock — 추가 잔액 변동 X)
        assertThat(userRepository.findPointBalance(userId)).isEqualTo(70_000L);
        // 출금 history 한 건 (환불 없음)
        var histories = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getPointType()).isEqualTo(PointHistoryType.출금);
    }

    @Test
    @DisplayName("동시 동일 idempotencyKey 신청_정확히 1건만 생성, 1번만 차감 (게이트 1 멱등성)")
    void 동시_동일_idempotencyKey_1건만() throws Exception {
        String key = idemKey();
        long amount = 30_000L;

        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicReference<Long> id1 = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Long> id2 = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();

        Runnable submit = () -> {
            try {
                start.await();
                Long id = txTemplate.execute(s -> withdrawalService.request(new WithdrawalRequestCommand(
                        userId, key, amount, "신한", "110-123-456789", "홍길동"
                )));
                if (id1.get() == null) id1.set(id); else id2.set(id);
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        };
        exec.submit(submit);
        exec.submit(submit);
        start.countDown();
        boolean finished = done.await(10, java.util.concurrent.TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 호출 모두 10초 안에 완료").isTrue();
        assertThat(err.get()).as("두 호출 모두 BusinessException 없이 완료 — 한 쪽은 dedup, 한 쪽은 신규").isNull();
        // 두 호출이 같은 id 반환해야 멱등 (DB UNIQUE 또는 select-then-insert dedup 둘 중 하나로 처리됨)
        assertThat(id1.get()).as("호출 1 id").isNotNull();
        assertThat(id2.get()).as("호출 2 id").isNotNull();
        assertThat(id1.get()).as("두 호출 같은 withdrawal id 반환").isEqualTo(id2.get());

        // withdrawal 행은 1건만, point_history 도 1건만 (=차감 1번)
        assertThat(withdrawalRepository.findByUserId(userId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .as("withdrawal 행 1건").hasSize(1);
        var histories = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(histories).as("출금 history 1건만 — 잔액 중복 차감 X").hasSize(1);
        assertThat(histories.get(0).getAmount()).isEqualTo(-amount);
        assertThat(userRepository.findPointBalance(userId)).isEqualTo(100_000L - amount);
    }

    private static String idemKey() {
        return "idem-" + java.util.UUID.randomUUID();
    }

    private Long persistUser() {
        User user = User.createSocialUser(
                SocialProvider.KAKAO,
                "kakao-" + System.nanoTime(),
                new Email("u-" + System.nanoTime() + "@example.com"),
                "user",
                null
        );
        em.persist(user);
        em.flush();
        return user.getId();
    }

    private Long persistAdmin() {
        // admins 테이블에 직접 native INSERT — admin 도메인 엔티티가 본 모듈에 노출 안 되어 있음
        String username = "admin-" + System.nanoTime();
        em.createNativeQuery("""
                INSERT INTO admins (username, password, name, role, is_active)
                VALUES (?, ?, ?, ?, ?)
                """)
                .setParameter(1, username)
                .setParameter(2, "$2a$10$dummyHashForITTestUseOnlyXX")
                .setParameter(3, "tester")
                .setParameter(4, "ADMIN")
                .setParameter(5, true)
                .executeUpdate();
        Object id = em.createNativeQuery("SELECT id FROM admins WHERE username = ?")
                .setParameter(1, username)
                .getSingleResult();
        return ((Number) id).longValue();
    }
}
