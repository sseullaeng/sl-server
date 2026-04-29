package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.config.JpaAuditingConfig;
import com.sseulang.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ItemQuerydslRepository} 통합 테스트 — testcontainers MySQL 8 + Flyway 실 마이그레이션.
 *
 * <p>Codex 게이트 2 권고: 검색 핵심 로직을 in-memory fake 로만 검증하면 prod QueryDSL/JPA/MySQL 의미와
 * 쉽게 드리프트한다. 본 IT 는 prod 와 동일한 MySQL 위에서 정렬, 삭제 제외, 태그 EXISTS, LIKE 결합을 검증.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaAuditingConfig.class, ItemQuerydslRepository.class})
@Testcontainers
class ItemQuerydslRepositoryIT {

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
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private EntityManager em;

    @Autowired
    private ItemQuerydslRepository repository;

    private Long sellerId;

    @BeforeEach
    void setUp() {
        // FK fk_items_seller 충족용 User 1건. (provider,id) UNIQUE 충돌 방지 위해 nano time.
        User user = User.createSocialUser(
                SocialProvider.KAKAO,
                "kakao-" + System.nanoTime(),
                new Email("it-" + System.nanoTime() + "@example.com"),
                "tester",
                null
        );
        em.persist(user);
        em.flush();
        sellerId = user.getId();
    }

    @Test
    @DisplayName("search 빈 criteria_삭제 제외 + createdAt desc 정렬")
    void search_정렬() {
        Item older = persistItem("older", TradeType.판매, 100L);
        sleepMs(20);
        Item middle = persistItem("middle", TradeType.판매, 100L);
        sleepMs(20);
        Item newer = persistItem("newer", TradeType.판매, 100L);

        Page<Item> result = repository.search(ItemSearchCriteria.empty(), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getId)
                .containsExactly(newer.getId(), middle.getId(), older.getId());
    }

    @Test
    @DisplayName("search 삭제 상태 제외")
    void search_삭제_제외() {
        Item alive = persistItem("alive", TradeType.판매, 100L);
        Item dead = persistItem("dead", TradeType.판매, 100L);
        dead.markAsDeleted();
        em.merge(dead);
        em.flush();
        em.clear();

        Page<Item> result = repository.search(ItemSearchCriteria.empty(), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getId)
                .containsExactly(alive.getId());
    }

    @Test
    @DisplayName("search q 부분일치")
    void search_q() {
        persistItem("아이폰 14 Pro", TradeType.판매, 100L);
        persistItem("갤럭시 S24", TradeType.판매, 100L);

        Page<Item> result = repository.search(
                new ItemSearchCriteria("아이폰", null, null, null, null, null),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getTitle)
                .containsExactly("아이폰 14 Pro");
    }

    @Test
    @DisplayName("search 가격 범위 + tradeType 결합")
    void search_결합() {
        persistItem("싼것", TradeType.판매, 1_000L);
        persistItem("중간_나눔", TradeType.나눔, 0L);
        Item target = persistItem("중간_판매", TradeType.판매, 50_000L);
        persistItem("비싼것", TradeType.판매, 1_000_000L);

        Page<Item> result = repository.search(
                new ItemSearchCriteria(null, null, TradeType.판매, 10_000L, 100_000L, null),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getId)
                .containsExactly(target.getId());
    }

    @Test
    @DisplayName("search tag EXISTS 서브쿼리")
    void search_tag() {
        Item iphone = persistItem("아이폰", TradeType.판매, 100L);
        iphone.addHashtag("미개봉");
        iphone.addHashtag("정품");
        em.merge(iphone);

        Item galaxy = persistItem("갤럭시", TradeType.판매, 100L);
        galaxy.addHashtag("정품");
        em.merge(galaxy);

        em.flush();
        em.clear();

        Page<Item> hit = repository.search(
                new ItemSearchCriteria(null, null, null, null, null, "미개봉"),
                PageRequest.of(0, 10));

        assertThat(hit.getContent())
                .extracting(Item::getId)
                .containsExactly(iphone.getId());
    }

    @Test
    @DisplayName("search 페이징 — total/페이지 동작")
    void search_페이징() {
        for (int i = 0; i < 12; i++) {
            persistItem("t" + i, TradeType.판매, 100L);
            sleepMs(5);
        }

        Page<Item> page0 = repository.search(ItemSearchCriteria.empty(), PageRequest.of(0, 5));
        Page<Item> page2 = repository.search(ItemSearchCriteria.empty(), PageRequest.of(2, 5));

        assertThat(page0.getTotalElements()).isEqualTo(12);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getContent()).hasSize(5);
        assertThat(page2.getContent()).hasSize(2);
    }

    private Item persistItem(String title, TradeType type, long price) {
        Long deposit = type.requiresDeposit() ? 10_000L : null;
        RentalUnit unit = type.requiresDeposit() ? RentalUnit.일 : null;
        Item item = Item.create(sellerId, null, title, "desc", price, deposit, unit, type, null);
        em.persist(item);
        em.flush();
        return item;
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
