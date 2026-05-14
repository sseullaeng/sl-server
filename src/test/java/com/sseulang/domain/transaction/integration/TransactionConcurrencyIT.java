package com.sseulang.domain.transaction.integration;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.infrastructure.persistence.CategoryRepositoryImpl;
import com.sseulang.domain.chat.domain.ChatRoom;
import com.sseulang.domain.file.application.NoOpPresignedUrlGenerator;
import com.sseulang.domain.item.application.NoOpWishlistView;
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
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.infrastructure.persistence.PointHistoryRepositoryImpl;
import com.sseulang.domain.user.application.UserApplicationService;
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
        com.sseulang.domain.item.application.ItemRentalActivityService.class,
        NoOpPresignedUrlGenerator.class,
        NoOpWishlistView.class,
        CategoryRepositoryImpl.class,
        CategoryApplicationService.class,
        UserRepositoryImpl.class,
        UserApplicationService.class,
        com.sseulang.domain.auth.application.NoOpRefreshTokenStore.class,
        com.sseulang.domain.auth.application.NoOpEmailSender.class,
        PointHistoryRepositoryImpl.class,
        PointApplicationService.class,
        TransactionRepositoryImpl.class,
        TransactionApplicationService.class,
        // v8b — UserApplicationService 가 UserReportRepository 도 의존
        com.sseulang.domain.report.infrastructure.persistence.UserReportRepositoryImpl.class,
        // 라운드 12 (#3.2) — TransactionApplicationService 가 ChatRoomApplicationService 의존 추가
        com.sseulang.domain.chat.infrastructure.persistence.ChatRoomRepositoryImpl.class,
        com.sseulang.domain.chat.application.ChatRoomApplicationService.class,
        com.sseulang.domain.user.infrastructure.persistence.UserViewAdapter.class,
        com.sseulang.domain.item.infrastructure.persistence.ItemViewAdapter.class,
        // 라운드 12 PR-C #6 — ChatRoomCardRepository 의존 (Mongo, DataJpaTest 라 NoOp).
        com.sseulang.domain.chat.application.NoOpChatRoomCardRepository.class,
        com.sseulang.domain.chat.application.NoOpTransactionView.class,
        com.sseulang.domain.chat.application.NoOpEscrowApplicationView.class,
        com.sseulang.domain.report.infrastructure.ItemReportViewAdapter.class
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
    private Long chatRoom1Id;
    private Long chatRoom2Id;
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
            // 라운드 11 — reserve 시 buyer 잔액 hold 되므로 사전 충전 필수
            em.createNativeQuery("UPDATE users SET point_balance = 50000 WHERE id IN (?, ?)")
                    .setParameter(1, buyer1Id)
                    .setParameter(2, buyer2Id)
                    .executeUpdate();

            itemId = itemService.register(ItemRegisterCommand.legacy(
                    sellerId, null, "물건", "설명", 50_000L, null, null, TradeType.판매,
                    "서울", null, null
            ));

            // 라운드 12 — 거래 시작은 채팅방 안에서만 + 판매자만. buyer 별 별도 채팅방.
            chatRoom1Id = persistChatRoom(itemId, sellerId, buyer1Id);
            chatRoom2Id = persistChatRoom(itemId, sellerId, buyer2Id);

            tx1Id = transactionService.create(new TransactionCreateCommand(itemId, sellerId, chatRoom1Id, null, null));
            tx2Id = transactionService.create(new TransactionCreateCommand(itemId, sellerId, chatRoom2Id, null, null));
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

        cleanup();
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

    /**
     * 라운드 11 — 같은 buyer 가 두 거래 동시 reserve 시도, 잔액이 한 건만 충당 가능.
     * Item 락이 없는 상황 (서로 다른 Item) 에서도 buyer 행 락이 직렬화 + atomic UPDATE 의 잔액 가드
     * (WHERE point_balance >= :amount) 가 두 번째 호출을 INSUFFICIENT_POINT 로 차단.
     */
    @Test
    @org.junit.jupiter.api.Disabled("라운드 12 — 직거래는 사이트 포인트 거래 없음. buyer hold race 시나리오 무의미.")
    @DisplayName("라운드 11 — 같은 buyer 두 거래 동시 reserve_잔액 한건만 충당_정확히 1건만 성공")
    void buyer_hold_race() throws Exception {
        // 별도 두 Item + 두 거래 (buyer 동일, 잔액 50000 한 건만 가능). 라운드 12 — 거래는 채팅방 안에서만 (판매자만).
        Long item2Id = txTemplate.execute(status -> itemService.register(ItemRegisterCommand.legacy(
                sellerId, null, "물건2", "설명", 50_000L, null, null, TradeType.판매, "서울", null, null
        )));
        // setUp 의 tx1Id (item1+buyer1, chatRoom1) 그대로 사용 — buyer1 의 첫 번째 거래.
        // 두 번째 거래는 item2 + 새 채팅방 (item2, seller, buyer1).
        Long chatRoomB = txTemplate.execute(status -> persistChatRoom(item2Id, sellerId, buyer1Id));
        Long txA = tx1Id;
        Long txB = txTemplate.execute(status ->
                transactionService.create(new TransactionCreateCommand(item2Id, sellerId, chatRoomB, null, null)));

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger otherError = new AtomicInteger();

        Runnable a = () -> tryReserveCounted(txA, start, done, success, insufficient, otherError);
        Runnable b = () -> tryReserveCounted(txB, start, done, success, insufficient, otherError);

        exec.submit(a);
        exec.submit(b);
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 호출 10초 안에 완료").isTrue();
        assertThat(success.get()).as("정확히 1건만 reserve 성공").isEqualTo(1);
        assertThat(insufficient.get()).as("나머지 1건은 INSUFFICIENT_POINT").isEqualTo(1);
        assertThat(otherError.get()).as("그 외 에러 없음").isZero();

        // buyer 잔액: 50000 → 0 (1건 hold), point_hold = 50000
        Long balance = (Long) em.createNativeQuery("SELECT point_balance FROM users WHERE id = ?")
                .setParameter(1, buyer1Id).getSingleResult();
        Long hold = (Long) em.createNativeQuery("SELECT point_hold FROM users WHERE id = ?")
                .setParameter(1, buyer1Id).getSingleResult();
        assertThat(balance).isZero();
        assertThat(hold).isEqualTo(50_000L);

        cleanup();
    }

    /**
     * 라운드 11 — 동일 거래의 reserve 직후 cancel / markHandover 동시 호출. Transaction 비관적 락이
     * 직렬화. cancel 이 먼저면 markHandover 가 TRANSACTION_INVALID_STATE, 반대도 마찬가지. 정확히 1건만 성공.
     */
    @Test
    @DisplayName("라운드 11 — 예약된 거래에 cancel / handover 동시_정확히 1건만 성공 (Transaction 락 직렬화)")
    void cancel_handover_race() throws Exception {
        // tx1 reserve (buyer1) 먼저
        txTemplate.execute(status -> {
            transactionService.reserve(tx1Id, sellerId);
            return null;
        });

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger cancelOk = new AtomicInteger();
        AtomicInteger handoverOk = new AtomicInteger();
        AtomicInteger invalidState = new AtomicInteger();
        AtomicInteger otherError = new AtomicInteger();

        Runnable cancelTask = () -> {
            try {
                start.await();
                transactionService.cancel(tx1Id, buyer1Id, "race");
                cancelOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.TRANSACTION_INVALID_STATE) invalidState.incrementAndGet();
                else otherError.incrementAndGet();
            } catch (Exception e) {
                otherError.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        Runnable handoverTask = () -> {
            try {
                start.await();
                transactionService.markHandover(tx1Id, sellerId);
                handoverOk.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.TRANSACTION_INVALID_STATE) invalidState.incrementAndGet();
                else otherError.incrementAndGet();
            } catch (Exception e) {
                otherError.incrementAndGet();
            } finally {
                done.countDown();
            }
        };

        exec.submit(cancelTask);
        exec.submit(handoverTask);
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(finished).as("두 호출 10초 안에 완료").isTrue();
        assertThat(cancelOk.get() + handoverOk.get()).as("정확히 1건만 성공").isEqualTo(1);
        assertThat(invalidState.get()).as("패배자는 TRANSACTION_INVALID_STATE").isEqualTo(1);
        assertThat(otherError.get()).as("그 외 에러 없음").isZero();

        // 결과 status 검증 — 취소 또는 인계완료 중 하나
        TransactionStatus finalStatus = txTemplate.execute(status ->
                transactionService.getById(tx1Id, sellerId).status());
        assertThat(finalStatus).isIn(TransactionStatus.취소, TransactionStatus.인계완료);

        cleanup();
    }

    private void tryReserveCounted(Long txId, CountDownLatch start, CountDownLatch done,
                                   AtomicInteger success, AtomicInteger insufficient, AtomicInteger otherError) {
        try {
            start.await();
            transactionService.reserve(txId, sellerId);
            success.incrementAndGet();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INSUFFICIENT_POINT) insufficient.incrementAndGet();
            else otherError.incrementAndGet();
        } catch (Exception e) {
            otherError.incrementAndGet();
        } finally {
            done.countDown();
        }
    }

    private void cleanup() {
        txTemplate.execute(status -> {
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM transactions").executeUpdate();
            em.createNativeQuery("DELETE FROM items").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
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

    private Long persistChatRoom(Long itemId, Long userA, Long userB) {
        ChatRoom cr = ChatRoom.openFor(itemId, userA, userB);
        em.persist(cr);
        em.flush();
        return cr.getId();
    }
}
