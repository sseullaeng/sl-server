package com.sseulang.domain.delivery.integration;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.delivery.application.dto.DeliveryCreateCommand;
import com.sseulang.domain.delivery.application.dto.DeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryRepository;
import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.delivery.infrastructure.persistence.DeliveryRepositoryImpl;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import com.sseulang.domain.point.infrastructure.persistence.PointHistoryRepositoryImpl;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.domain.user.infrastructure.persistence.UserRepositoryImpl;
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

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 9 게이트 1 round 1 보강 — 배달 정산/수락/취소 동시성 DB-backed IT.
 *
 * <p>Codex 게이트 1 Critical 1·2 회귀 차단:
 * <ol>
 *   <li>{@code 동시 complete} — PESSIMISTIC_WRITE 직렬화 → 정확히 1건만 정산 (잔액 1회 변동, history 2건)</li>
 *   <li>{@code 동시 accept} — conditional UPDATE → 정확히 1건만 수락</li>
 *   <li>{@code accept vs cancel race} — 두 conditional UPDATE 가 동일 status='모집중' 가드 →
 *       정확히 1건만 성공, 다른 1건은 INVALID_STATE 거부</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserApplicationService.class,
        com.sseulang.domain.auth.application.NoOpRefreshTokenStore.class,
        com.sseulang.domain.auth.application.NoOpEmailSender.class,
        // UserApplicationService 가 v8b 부터 Transaction/UserReport repo 의존 — slice 에 명시 추가.
        // (Clock 은 SseulangApplication @Bean 이 자동 제공)
        com.sseulang.domain.transaction.infrastructure.persistence.TransactionRepositoryImpl.class,
        com.sseulang.domain.report.infrastructure.persistence.UserReportRepositoryImpl.class,
        PointHistoryRepositoryImpl.class,
        PointApplicationService.class,
        DeliveryRepositoryImpl.class,
        DeliveryApplicationService.class,
        com.sseulang.domain.delivery.application.NoOpDeliveryLocationCache.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryConcurrencyIT {

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
    @Autowired private DeliveryApplicationService deliveryService;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;
    private Long requesterId;
    private Long riderId;
    private Long otherRiderId;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(s -> {
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM deliveries").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
        requesterId = txTemplate.execute(s -> persistVerifiedUser("req"));
        riderId = txTemplate.execute(s -> persistVerifiedUser("rider1"));
        otherRiderId = txTemplate.execute(s -> persistVerifiedUser("rider2"));
        txTemplate.execute(s -> {
            userRepository.creditPointBalance(requesterId, 50_000L);
            return null;
        });
    }

    @Test
    @DisplayName("동시 complete 2건_정확히 1건만 정산 (게이트 1 Critical 1)")
    void 동시_complete_1건만_정산() throws Exception {
        Long deliveryId = setupDeliveredDelivery(5_000L);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger stateErr = new AtomicInteger();
        AtomicReference<Throwable> otherError = new AtomicReference<>();

        Runnable complete = () -> {
            try {
                start.await();
                txTemplate.execute(s -> deliveryService.complete(deliveryId, requesterId));
                okCount.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.DELIVERY_INVALID_STATE) {
                    stateErr.incrementAndGet();
                }
            } catch (Exception e) {
                otherError.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        };
        exec.submit(complete);
        exec.submit(complete);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 complete 모두 15초 안에 완료").isTrue();
        assertThat(otherError.get()).as("BusinessException 외 예외 없음").isNull();
        assertThat(okCount.get()).as("정확히 1건만 성공").isEqualTo(1);
        assertThat(stateErr.get()).as("나머지 1건은 상태 가드로 거부").isEqualTo(1);

        // 잔액 — 요청자 50000-5000=45000, 라이더 0+5000=5000 (1회만 변동)
        assertThat(userRepository.findPointBalance(requesterId)).isEqualTo(45_000L);
        assertThat(userRepository.findPointBalance(riderId)).isEqualTo(5_000L);

        // history — 정확히 2건 (배달결제 + 배달정산)
        long deliveryHistoryCount = pointHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(requesterId).stream()
                .filter(h -> h.getReferenceId() != null && h.getReferenceId().equals(deliveryId)).count()
                + pointHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(riderId).stream()
                .filter(h -> h.getReferenceId() != null && h.getReferenceId().equals(deliveryId)).count();
        assertThat(deliveryHistoryCount).as("정확히 2건 (요청자 결제 + 라이더 정산)").isEqualTo(2);

        // 상태
        DeliveryRequest after = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(DeliveryStatus.정산완료);
    }

    @Test
    @DisplayName("동시 accept 2건_정확히 1건만 수락 (게이트 1 W-3 + race)")
    void 동시_accept_1건만() throws Exception {
        Long deliveryId = txTemplate.execute(s -> deliveryService.create(new DeliveryCreateCommand(
                requesterId, "출발지", "도착지", "물품", 3_000L, null, null
        )).id());

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicReference<Throwable> otherError = new AtomicReference<>();

        java.util.function.Function<Long, Runnable> acceptFor = (Long rId) -> () -> {
            try {
                start.await();
                txTemplate.execute(s -> deliveryService.accept(deliveryId, rId));
                okCount.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.DELIVERY_ALREADY_ACCEPTED) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherError.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        };
        exec.submit(acceptFor.apply(riderId));
        exec.submit(acceptFor.apply(otherRiderId));
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 accept 모두 10초 안에 완료").isTrue();
        assertThat(otherError.get()).as("BusinessException 외 예외 없음").isNull();
        assertThat(okCount.get()).as("정확히 1건만 성공").isEqualTo(1);
        assertThat(conflictCount.get()).as("나머지 1건은 ALREADY_ACCEPTED").isEqualTo(1);

        DeliveryRequest after = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(DeliveryStatus.수락);
        assertThat(after.getRiderId()).isIn(riderId, otherRiderId);
    }

    @Test
    @DisplayName("accept vs cancel race_정확히 1건만 성공 (게이트 1 Critical 2)")
    void accept_vs_cancel_race() throws Exception {
        Long deliveryId = txTemplate.execute(s -> deliveryService.create(new DeliveryCreateCommand(
                requesterId, "출발지", "도착지", "물품", 3_000L, null, null
        )).id());

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger acceptOk = new AtomicInteger();
        AtomicInteger cancelOk = new AtomicInteger();
        AtomicInteger stateErr = new AtomicInteger();
        AtomicReference<Throwable> otherError = new AtomicReference<>();

        exec.submit(() -> {
            try {
                start.await();
                txTemplate.execute(s -> deliveryService.accept(deliveryId, riderId));
                acceptOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.DELIVERY_ALREADY_ACCEPTED
                        || e.getErrorCode() == ErrorCode.DELIVERY_INVALID_STATE) {
                    stateErr.incrementAndGet();
                }
            } catch (Exception e) {
                otherError.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        });
        exec.submit(() -> {
            try {
                start.await();
                txTemplate.execute(s -> deliveryService.cancel(deliveryId, requesterId, "변심"));
                cancelOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.DELIVERY_INVALID_STATE) {
                    stateErr.incrementAndGet();
                }
            } catch (Exception e) {
                otherError.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        });
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 호출 모두 10초 안에 완료").isTrue();
        assertThat(otherError.get()).as("BusinessException 외 예외 없음").isNull();
        assertThat(acceptOk.get() + cancelOk.get()).as("정확히 1건만 성공").isEqualTo(1);
        assertThat(stateErr.get()).as("나머지 1건은 상태 가드로 거부").isEqualTo(1);

        DeliveryRequest after = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(after.getStatus()).isIn(DeliveryStatus.수락, DeliveryStatus.취소);
    }

    /** 정산 진입 가능한 상태(배송완료) 까지 만들어 둠. */
    private Long setupDeliveredDelivery(long fee) {
        return txTemplate.execute(s -> {
            DeliveryResult created = deliveryService.create(new DeliveryCreateCommand(
                    requesterId, "출발지", "도착지", "물품", fee, null, null
            ));
            deliveryService.accept(created.id(), riderId);
            deliveryService.markPickedUp(created.id(), riderId);
            deliveryService.markDelivered(created.id(), riderId);
            return created.id();
        });
    }

    private Long persistVerifiedUser(String tag) {
        User user = User.createSocialUser(
                SocialProvider.KAKAO,
                "kakao-" + tag + "-" + System.nanoTime(),
                new Email(tag + "-" + System.nanoTime() + "@d.test"),
                tag + "-" + System.nanoTime(),
                null
        );
        em.persist(user);
        em.flush();
        return user.getId();
    }
}
