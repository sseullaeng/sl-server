package com.sseulang.domain.transaction.integration;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.TransactionCascadeService;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.infrastructure.persistence.TransactionRepositoryImpl;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-5 cascade 격리 IT (Codex 게이트 2 후속 라운드 2 #1 검증).
 * 핵심: TransactionTemplate(REQUIRES_NEW) 가 실제 Spring 트랜잭션 매니저로 row 단위 격리되는지.
 *
 * <p>같은 chatRoom 의 직거래 두 건 (하나는 정상, 하나는 권한 불일치) 을 cascade.
 * 권한 불일치 row 는 BusinessException 으로 자체 tx 롤백되지만, 정상 row 는 별도 tx 로 commit 되어
 * 살아남는지 확인. AOP self-invocation 함정에 빠지면 둘 다 같은 외부 tx 안에서 처리되어
 * 본 IT 의 격리 보장이 증명되지 않음.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        TransactionRepositoryImpl.class,
        TransactionCascadeService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionCascadeIT {

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
    @Autowired private TransactionRepository txRepo;
    @Autowired private TransactionCascadeService cascadeService;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate txTemplate;

    private static final Long ITEM_ID = 100L;
    private static final Long SELLER = 10L;
    private static final Long BUYER = 20L;
    private static final Long CHAT_ROOM = 7L;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(txManager);
        txTemplate.executeWithoutResult(s -> {
            em.createNativeQuery("DELETE FROM transactions").executeUpdate();
            em.createNativeQuery("DELETE FROM items WHERE id = :id").setParameter("id", ITEM_ID).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id IN (:s, :b)")
                    .setParameter("s", SELLER).setParameter("b", BUYER).executeUpdate();
            // FK 만족용 seed — cascade 자체 검증엔 영향 X.
            em.createNativeQuery(
                    "INSERT INTO users (id, email, nickname, email_verified, point_balance, point_hold, "
                            + "version, review_count, rating_sum, is_blocked, is_deleted, is_rider, "
                            + "cumulative_suspend_days, created_at, updated_at) VALUES "
                            + "(:s, 'cascade-seller@x.com', 'cascade-seller', 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, NOW(), NOW()),"
                            + "(:b, 'cascade-buyer@x.com',  'cascade-buyer',  1, 0, 0, 0, 0, 0, 0, 0, 0, 0, NOW(), NOW())"
            ).setParameter("s", SELLER).setParameter("b", BUYER).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO items (id, seller_id, title, description, price, deposit, deposit_type, "
                            + "trade_type, trade_types, status, view_count, wishlist_count, created_at, updated_at) VALUES "
                            + "(:id, :seller, 'cascade-it', 'desc', 0, NULL, 'AMOUNT', "
                            + "'판매', '판매', '판매중', 0, 0, NOW(), NOW())"
            ).setParameter("id", ITEM_ID).setParameter("seller", SELLER).executeUpdate();
            em.createNativeQuery("DELETE FROM chat_rooms WHERE id = :id")
                    .setParameter("id", CHAT_ROOM).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO chat_rooms (id, item_id, user1_id, user2_id, is_active, created_at, updated_at) VALUES "
                            + "(:id, :item, :s, :b, 1, NOW(), NOW())"
            ).setParameter("id", CHAT_ROOM).setParameter("item", ITEM_ID)
                    .setParameter("s", SELLER).setParameter("b", BUYER).executeUpdate();
        });
    }

    @Test
    @DisplayName("cascade row 단위 격리 — 실패 row 는 롤백, 정상 row 는 commit (REQUIRES_NEW 실증)")
    void cascade_격리_검증() {
        Long goodTxId = txTemplate.execute(s -> persist(TradeType.판매, TransactionStatus.예약, null));
        // 대여 거래는 도메인 가드(canConfirmReturn 흐름) 로 BusinessException → 정책 스킵
        Long policyFailTxId = txTemplate.execute(s -> persist(TradeType.대여, TransactionStatus.예약,
                LocalDateTime.now().plusDays(1)));

        var result = cascadeService.cascadeCompleteByChatRoom(CHAT_ROOM, BUYER, SELLER);

        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.policySkipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();

        // 정상 row 는 거래완료 commit (별도 tx 라 본 검증 시점에 이미 영속화됨)
        Transaction good = txTemplate.execute(s -> txRepo.findById(goodTxId).orElseThrow());
        assertThat(good.getStatus()).isEqualTo(TransactionStatus.거래완료);
        assertThat(good.getCompletedAt()).isNotNull();

        // 대여 row 는 그대로 — BusinessException 으로 별도 tx 롤백
        Transaction rental = txTemplate.execute(s -> txRepo.findById(policyFailTxId).orElseThrow());
        assertThat(rental.getStatus()).isEqualTo(TransactionStatus.예약);
    }

    @Test
    @DisplayName("cascade 후보 0건_no-op")
    void cascade_빈_chatRoom() {
        var result = cascadeService.cascadeCompleteByChatRoom(CHAT_ROOM, BUYER, SELLER);
        assertThat(result.completed()).isZero();
        assertThat(result.policySkipped()).isZero();
        assertThat(result.failed()).isZero();
    }

    private Long persist(TradeType type, TransactionStatus status, LocalDateTime rentalStart) {
        Long deposit = type == TradeType.대여 ? 10_000L : null;
        LocalDateTime end = rentalStart != null ? rentalStart.plusDays(2) : null;
        Transaction tx = Transaction.create(ITEM_ID, SELLER, BUYER, type, 50_000L,
                deposit, rentalStart, end, CHAT_ROOM);
        ReflectionTestUtils.setField(tx, "status", status);
        return txRepo.save(tx).getId();
    }
}
