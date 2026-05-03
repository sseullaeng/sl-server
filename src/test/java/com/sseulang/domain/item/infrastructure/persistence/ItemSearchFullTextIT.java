package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.domain.Item;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FULLTEXT(ngram) IT (follow-up #10).
 *
 * <p>InnoDB FULLTEXT 인덱스는 commit 된 데이터만 매칭하므로 일반 @DataJpaTest 의 rollback 트랜잭션
 * 안에서 persist 한 row 는 매칭되지 않는다. 본 클래스는 클래스 단위 {@code NOT_SUPPORTED} 로 트랜잭션을
 * 끄고, 시드 INSERT 마다 명시적으로 commit 하여 prod 와 동일한 가시성으로 검증.</p>
 *
 * <p>cleanup: 각 테스트 시작 시 {@code DELETE FROM items} + {@code DELETE FROM users} 직접 실행.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaAuditingConfig.class, ItemQuerydslRepository.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ItemSearchFullTextIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sseulang_test")
            .withUsername("test")
            .withPassword("testpw")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--ngram_token_size=2",
                    "--innodb_ft_min_token_size=2");

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
    @Autowired private ItemQuerydslRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long sellerId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.execute(s -> {
            em.createNativeQuery("DELETE FROM item_hashtags").executeUpdate();
            em.createNativeQuery("DELETE FROM item_images").executeUpdate();
            em.createNativeQuery("DELETE FROM items").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
        sellerId = tx.execute(s -> {
            User user = User.createSocialUser(
                    SocialProvider.KAKAO,
                    "kakao-ft-" + System.nanoTime(),
                    new Email("ft-" + System.nanoTime() + "@example.com"),
                    "ft-tester",
                    null
            );
            em.persist(user);
            em.flush();
            return user.getId();
        });
    }

    @Test
    @DisplayName("한글 부분 ngram 매칭 — '아이폰' → '아이폰 14 Pro'")
    void ngram_한글_매칭() {
        seedItem("아이폰 14 Pro", "디테일 없음");
        seedItem("갤럭시 S24", "디테일 없음");

        Page<Item> result = repository.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria("아이폰", null, null, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getTitle)
                .containsExactly("아이폰 14 Pro");
    }

    @Test
    @DisplayName("두 토큰 AND — '아이폰 미개봉' = +아이폰 +미개봉")
    void boolean_and() {
        seedItem("아이폰 14 미개봉", "x");
        seedItem("아이폰 13 사용감 있음", "x");
        seedItem("갤럭시 미개봉", "x");

        Page<Item> result = repository.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria("아이폰 미개봉", null, null, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getTitle)
                .containsExactly("아이폰 14 미개봉");
    }

    @Test
    @DisplayName("description 매칭 — title+description 인덱스")
    void description_매칭() {
        seedItem("물건1", "정품 갤럭시 액세서리");
        seedItem("물건2", "다른 제품 설명");

        Page<Item> result = repository.search(
                new com.sseulang.domain.item.application.dto.ItemSearchCriteria("갤럭시", null, null, null, null, null, null, com.sseulang.domain.item.application.dto.ItemSort.LATEST),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Item::getTitle)
                .containsExactly("물건1");
    }

    private void seedItem(String title, String description) {
        tx.execute(s -> {
            Item item = Item.create(sellerId, null, title, description, 100L, null, null, TradeType.판매, null);
            em.persist(item);
            em.flush();
            return null;
        });
    }
}
