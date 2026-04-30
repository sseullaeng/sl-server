package com.sseulang.domain.transaction.integration;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.infrastructure.persistence.CategoryRepositoryImpl;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.item.infrastructure.persistence.ItemQuerydslRepository;
import com.sseulang.domain.item.infrastructure.persistence.ItemRepositoryImpl;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.point.infrastructure.persistence.PointHistoryRepositoryImpl;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.infrastructure.persistence.TransactionRepositoryImpl;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Day 8 게이트 1 보강 — 정산 실패 시 DB 트랜잭션 롤백을 실 MySQL 경로로 입증.
 *
 * <ol>
 *   <li>{@code complete 정산 실패} — buyer 잔액 부족 → INSUFFICIENT_POINT → Item / Transaction /
 *       PointHistory 모두 원복 (Item.status=예약 유지, point_histories 빈 상태)</li>
 *   <li>{@code transfer sellerId<buyerId 분기 롤백} — seller credit 선행된 후 buyer deduct 가
 *       잔액 부족으로 실패 → 같은 트랜잭션 롤백으로 seller balance 도 0 유지, history 미적재</li>
 * </ol>
 *
 * <p>fake repository 는 트랜잭션 롤백 시뮬을 못 하므로 본 IT 가 prod 정합성의 마지막 안전망.
 * 본 IT 통과 = Day 8 게이트 1 통과 기준 (Codex 2차 재리뷰 합의).</p>
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
        UserApplicationService.class,
        PointHistoryRepositoryImpl.class,
        PointApplicationService.class,
        TransactionRepositoryImpl.class,
        TransactionApplicationService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementRollbackIT {

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
    @Autowired private ItemApplicationService itemService;
    @Autowired private TransactionApplicationService transactionService;
    @Autowired private PointApplicationService pointService;
    @Autowired private ItemRepository itemRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            // 깨끗한 상태에서 시작
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM transactions").executeUpdate();
            em.createNativeQuery("DELETE FROM items").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("complete 정산 실패 (buyer 잔액 부족)_Item/Tx/PointHistory 모두 원복")
    void complete_정산_실패_롤백() {
        Long sellerId = txTemplate.execute(s -> persistUser("seller"));
        Long buyerId = txTemplate.execute(s -> persistUser("buyer"));
        // buyer 잔액 = 10000 (price 50000 보다 부족)
        txTemplate.execute(s -> {
            userRepository.creditPointBalance(buyerId, 10_000L);
            return null;
        });

        Long itemId = txTemplate.execute(s -> itemService.register(new ItemRegisterCommand(
                sellerId, null, "물건", "설명", 50_000L, null, null, TradeType.판매,
                "서울", null, null
        )));
        Long txId = txTemplate.execute(s -> transactionService.create(
                new TransactionCreateCommand(itemId, buyerId, null, null)
        ));
        txTemplate.execute(s -> {
            transactionService.reserve(txId, sellerId);
            return null;
        });

        // 정산 시도 → buyer 잔액 부족으로 INSUFFICIENT_POINT
        assertThatThrownBy(() -> txTemplate.execute(s -> {
            transactionService.complete(txId, sellerId);
            return null;
        }))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        // verify — Item / Transaction / PointHistory / 잔액 모두 원복
        Item item = itemRepository.findById(itemId).orElseThrow();
        assertThat(item.getStatus()).as("Item 은 예약 상태 유지 (거래완료 전이 X)").isEqualTo(ItemStatus.예약);

        Transaction tx = transactionRepository.findById(txId).orElseThrow();
        assertThat(tx.getStatus()).as("Transaction 은 예약 상태 유지").isEqualTo(TransactionStatus.예약);
        assertThat(tx.getCompletedAt()).isNull();

        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(buyerId))
                .as("buyer history 미적재").isEmpty();
        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(sellerId))
                .as("seller history 미적재 (선행 적립도 함께 롤백)").isEmpty();

        assertThat(userRepository.findPointBalance(buyerId)).isEqualTo(10_000L);
        assertThat(userRepository.findPointBalance(sellerId)).isZero();
    }

    @Test
    @DisplayName("transfer sellerId<buyerId 분기_buyer 잔액 부족_seller 선행 적립도 롤백")
    void transfer_seller먼저_buyer잔액부족_롤백() {
        // sellerId < buyerId 시나리오 강제 — seller 를 먼저 persist 해서 작은 ID 부여
        Long sellerId = txTemplate.execute(s -> persistUser("seller"));
        Long buyerId = txTemplate.execute(s -> persistUser("buyer"));
        assertThat(sellerId).isLessThan(buyerId);  // sellerId < buyerId 보장

        // buyer 잔액 부족 (10000 < 50000)
        txTemplate.execute(s -> {
            userRepository.creditPointBalance(buyerId, 10_000L);
            return null;
        });

        // transfer 호출 — id-asc 로 seller credit 먼저 → buyer deduct 실패
        assertThatThrownBy(() -> txTemplate.execute(s -> {
            pointService.transfer(buyerId, sellerId, 50_000L,
                    PointReferenceType.TRANSACTION, 999L, "test");
            return null;
        }))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        // verify — seller 적립 / buyer 차감 / history 모두 미반영
        assertThat(userRepository.findPointBalance(sellerId))
                .as("seller 선행 적립도 롤백").isZero();
        assertThat(userRepository.findPointBalance(buyerId))
                .as("buyer 잔액 변동 X").isEqualTo(10_000L);
        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(sellerId))
                .as("seller history 미적재").isEmpty();
        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(buyerId))
                .as("buyer history 미적재").isEmpty();
    }

    @Test
    @DisplayName("transfer 정상_id-asc 락 순서로 commit_history 두 건 적재")
    void transfer_정상_commit() {
        Long sellerId = txTemplate.execute(s -> persistUser("seller"));
        Long buyerId = txTemplate.execute(s -> persistUser("buyer"));
        txTemplate.execute(s -> {
            userRepository.creditPointBalance(buyerId, 50_000L);
            return null;
        });

        txTemplate.execute(s -> {
            pointService.transfer(buyerId, sellerId, 50_000L,
                    PointReferenceType.TRANSACTION, 1L, "거래 결제");
            return null;
        });

        assertThat(userRepository.findPointBalance(buyerId)).isZero();
        assertThat(userRepository.findPointBalance(sellerId)).isEqualTo(50_000L);
        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(buyerId)).hasSize(1);
        assertThat(pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(sellerId)).hasSize(1);
        PointHistory buyerHistory = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(buyerId).get(0);
        assertThat(buyerHistory.getAmount()).isEqualTo(-50_000L);
        assertThat(buyerHistory.getBalanceAfter()).isZero();
        PointHistory sellerHistory = pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(sellerId).get(0);
        assertThat(sellerHistory.getAmount()).isEqualTo(50_000L);
        assertThat(sellerHistory.getBalanceAfter()).isEqualTo(50_000L);
        assertThat(sellerHistory.getReferenceType()).isEqualTo(PointReferenceType.TRANSACTION);
        assertThat(sellerHistory.getReferenceId()).isEqualTo(1L);
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
