package com.sseulang.domain.transaction.integration;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.infrastructure.persistence.CategoryRepositoryImpl;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.item.infrastructure.persistence.ItemQuerydslRepository;
import com.sseulang.domain.item.infrastructure.persistence.ItemRepositoryImpl;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.infrastructure.persistence.TransactionRepositoryImpl;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.infrastructure.persistence.UserRepositoryImpl;
import com.sseulang.global.config.JpaAuditingConfig;
import com.sseulang.global.config.QuerydslConfig;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transaction 동시 reserve race 검증 — 가이드 §5.2 / §7.6 강제 영역.
 *
 * <p>같은 Item 에 두 buyer 가 동시 reserve 호출 시 비관적 락(PESSIMISTIC_WRITE) 으로 첫 트랜잭션이
 * Item 락 점유 → 두 번째는 대기 → 첫 commit 후 두 번째가 status=예약 보고 거부. 정확히 1건만 성공해야.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        QuerydslConfig.class,
        JpaAuditingConfig.class,
        ItemQuerydslRepository.class,
        ItemRepositoryImpl.class,
        ItemApplicationService.class,
        CategoryRepositoryImpl.class,
        CategoryApplicationService.class,
        UserRepositoryImpl.class,
        TransactionRepositoryImpl.class,
        TransactionApplicationService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionConcurrencyIT {

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

    @Autowired
    private EntityManager em;

    @Autowired
    private ItemApplicationService itemService;

    @Autowired
    private TransactionApplicationService transactionService;

    @Autowired
    private com.sseulang.domain.item.domain.ItemRepository itemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;

    private Long sellerId;
    private Long buyer1Id;
    private Long buyer2Id;
    private Long itemId;
    private Long tx1Id;
    private Long tx2Id;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        // setup 은 별도 트랜잭션 안에서 commit (기본 테스트 트랜잭션 X — @Commit 적용)
        txTemplate.execute(status -> {
            sellerId = persistUser("seller");
            buyer1Id = persistUser("buyer1");
            buyer2Id = persistUser("buyer2");

            itemId = itemService.register(new ItemRegisterCommand(
                    sellerId, null, "물건", "설명", 50_000L, null, null, TradeType.판매,
                    "서울", null, null
            ));

            tx1Id = transactionService.create(new TransactionCreateCommand(itemId, buyer1Id, null, null));
            tx2Id = transactionService.create(new TransactionCreateCommand(itemId, buyer2Id, null, null));
            return null;
        });
    }

    @Test
    @DisplayName("동시 reserve_정확히 1건 성공 + Item.status=예약")
    void 동시_reserve() throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger reservedByOther = new AtomicInteger();
        AtomicInteger otherError = new AtomicInteger();

        Runnable reserveTx1 = () -> tryReserve(tx1Id, start, done, success, reservedByOther, otherError);
        Runnable reserveTx2 = () -> tryReserve(tx2Id, start, done, success, reservedByOther, otherError);

        exec.submit(reserveTx1);
        exec.submit(reserveTx2);

        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 호출 모두 10초 안에 완료").isTrue();
        assertThat(success.get()).as("정확히 1건만 reserve 성공").isEqualTo(1);
        assertThat(reservedByOther.get()).as("나머지 1건은 TRANSACTION_RESERVED_BY_OTHER").isEqualTo(1);
        assertThat(otherError.get()).as("그 외 에러 없음").isZero();

        Item item = itemRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.예약);

        // cleanup — @Commit 으로 데이터 남으니 후속 테스트 충돌 방지 (현재 클래스 단일 테스트라 실효 X, 안전 차원)
        txTemplate.execute(status -> {
            em.createNativeQuery("DELETE FROM transactions").executeUpdate();
            em.createNativeQuery("DELETE FROM items").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
    }

    private void tryReserve(Long txId, CountDownLatch start, CountDownLatch done,
                            AtomicInteger success, AtomicInteger reservedByOther, AtomicInteger otherError) {
        try {
            start.await();
            transactionService.reserve(txId, sellerId);
            success.incrementAndGet();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.TRANSACTION_RESERVED_BY_OTHER) {
                reservedByOther.incrementAndGet();
            } else {
                otherError.incrementAndGet();
            }
        } catch (Exception e) {
            otherError.incrementAndGet();
        } finally {
            done.countDown();
        }
    }

    private Long persistUser(String suffix) {
        User user = User.createSocialUser(
                SocialProvider.KAKAO,
                "kakao-" + suffix + "-" + System.nanoTime(),
                new Email(suffix + "-" + System.nanoTime() + "@example.com"),
                suffix,
                null
        );
        em.persist(user);
        em.flush();
        return user.getId();
    }
}
