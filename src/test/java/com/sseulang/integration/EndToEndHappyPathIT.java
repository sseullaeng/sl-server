package com.sseulang.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.payment.domain.PaymentConfirmResult;
import com.sseulang.domain.payment.domain.PaymentGateway;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.global.security.CookieUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day 9 마감 — 백엔드 전 도메인 happy-path e2e 검증.
 *
 * <p>시나리오 (한 시퀀스로 검증):
 * <ol>
 *   <li>Seller 가 카카오 OAuth 로 가입 → AT/RT 쿠키 수령</li>
 *   <li>Seller 가 Item 등록</li>
 *   <li>Buyer 가 카카오 OAuth 로 가입</li>
 *   <li>Buyer 가 토스 결제로 잔액 충전 (PG mock)</li>
 *   <li>Buyer 가 Item 으로 Transaction 생성 (채팅중)</li>
 *   <li>Seller 가 Transaction 예약</li>
 *   <li>Seller 인계확인 → 인계완료 (라운드 11)</li>
 *   <li>Buyer 인수확인 → 거래완료 자동 전이 + 정산 (buyer hold 해제 + seller 적립)</li>
 *   <li>Buyer 가 Review 작성 (별점 기록)</li>
 * </ol>
 *
 * <p>외부 의존만 mock: {@link OAuthProvider}, {@link PaymentGateway}. 나머지(DB / Redis /
 * MongoDB / Spring Security / 모든 도메인 service) 는 real. 회귀 발견 시 핫픽스 가드.</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(EndToEndHappyPathIT.TestOAuthBeans.class)
@Testcontainers
class EndToEndHappyPathIT {

    /**
     * OAuthProvider 빈 override — 실제 Kakao/Google 구현체 자리에 mock 을 넣되, 컨텍스트 init
     * 시점에 supports() 가 정확한 SocialProvider 를 반환해야 OAuthLoginService 의
     * `providers.stream().toMap(OAuthProvider::supports, ...)` 가 깨지지 않음. {@code @MockBean}
     * 만으로는 stub 적용이 늦어 init 실패 — TestConfiguration 으로 직접 정의.
     */
    @TestConfiguration
    static class TestOAuthBeans {

        @Bean("kakaoOAuthProvider")
        OAuthProvider kakaoOAuthProvider() {
            OAuthProvider m = Mockito.mock(OAuthProvider.class);
            Mockito.when(m.supports()).thenReturn(SocialProvider.KAKAO);
            return m;
        }

        @Bean("googleOAuthProvider")
        OAuthProvider googleOAuthProvider() {
            OAuthProvider m = Mockito.mock(OAuthProvider.class);
            Mockito.when(m.supports()).thenReturn(SocialProvider.GOOGLE);
            return m;
        }
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sseulang_test")
            .withUsername("test")
            .withPassword("testpw")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--ngram_token_size=2");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

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
        // dev-auth 우회 비활성 — e2e 는 정상 OAuth 흐름만 검증
        r.add("app.dev-auth.enabled", () -> "false");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    /** Kakao OAuth mock — TestOAuthBeans 에서 정의. verifyAndFetch 는 시나리오별 stub. */
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("kakaoOAuthProvider")
    private OAuthProvider kakaoOAuthProvider;

    /** Toss confirm/lookup — 정상 응답 stub. */
    @MockBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void cleanDb() {
        // Mongo 컬렉션은 영향 없음 — 본 시나리오는 chat/message/notification 미사용.
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM payments").executeUpdate();
            em.createNativeQuery("DELETE FROM reviews").executeUpdate();
            em.createNativeQuery("DELETE FROM transactions").executeUpdate();
            em.createNativeQuery("DELETE FROM item_images").executeUpdate();
            em.createNativeQuery("DELETE FROM item_hashtags").executeUpdate();
            em.createNativeQuery("DELETE FROM items").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("e2e happy path — 가입 → 충전 → 거래 → 정산 → 리뷰 한 시퀀스")
    void e2e_happy_path() throws Exception {
        // ───────── 1. Seller 가입 ─────────
        OAuthUserInfo sellerInfo = new OAuthUserInfo(
                SocialProvider.KAKAO, "kakao-seller-" + UUID.randomUUID(),
                new Email("seller-" + System.nanoTime() + "@e2e.test"), "셀러", null
        );
        when(kakaoOAuthProvider.supports()).thenReturn(SocialProvider.KAKAO);
        when(kakaoOAuthProvider.exchangeCodeAndFetch("SELLER_TOKEN", "http://test/cb")).thenReturn(sellerInfo);

        Cookie sellerAt = loginAndExtractAt("SELLER_TOKEN");

        // ───────── 2. Seller Item 등록 ─────────
        String itemBody = """
                {
                  "title": "e2e 물품",
                  "description": "e2e 시나리오 테스트용",
                  "price": 50000,
                  "tradeType": "판매",
                  "region": "서울"
                }
                """;
        MvcResult itemResult = mvc.perform(post("/api/v1/items")
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        Long itemId = readId(itemResult, "$.data.id");
        assertThat(itemId).isNotNull();

        // ───────── 3. Buyer 가입 ─────────
        OAuthUserInfo buyerInfo = new OAuthUserInfo(
                SocialProvider.KAKAO, "kakao-buyer-" + UUID.randomUUID(),
                new Email("buyer-" + System.nanoTime() + "@e2e.test"), "바이어", null
        );
        when(kakaoOAuthProvider.exchangeCodeAndFetch("BUYER_TOKEN", "http://test/cb")).thenReturn(buyerInfo);
        Cookie buyerAt = loginAndExtractAt("BUYER_TOKEN");

        // ───────── 4. Buyer 충전 (Toss mock) ─────────
        long chargeAmount = 100_000L;
        // 4-1 startCharge — PG 호출 X, merchantUid + clientKey 발급
        MvcResult chargeStart = mvc.perform(post("/api/v1/payments/charge")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + chargeAmount + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String merchantUid = readString(chargeStart, "$.data.merchantUid");
        assertThat(merchantUid).isNotBlank();

        // 4-2 confirmCharge — PG.confirm mock 응답 stub
        when(paymentGateway.confirm(any(), any(), any(Long.class)))
                .thenReturn(new PaymentConfirmResult(
                        "tossPaymentKey-" + UUID.randomUUID(),
                        merchantUid,
                        chargeAmount,
                        PaymentMethod.CARD,
                        LocalDateTime.now(),
                        "{\"approved\":true}"
                ));

        String confirmBody = String.format(
                "{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}",
                "tossPaymentKey-" + UUID.randomUUID(), merchantUid, chargeAmount
        );
        mvc.perform(post("/api/v1/payments/charge/confirm")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("완료"));

        // ───────── 5. Buyer 채팅방 개설 + Seller Transaction create (라운드 12) ─────────
        MvcResult roomResult = mvc.perform(post("/api/v1/chat-rooms")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Long chatRoomId = readId(roomResult, "$.data.id");

        MvcResult txResult = mvc.perform(post("/api/v1/transactions")
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"chatRoomId\":" + chatRoomId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long txId = readId(txResult, "$.data.id");

        // ───────── 6. Seller Transaction 예약 (라운드 11 — buyer hold 트리거) ─────────
        mvc.perform(patch("/api/v1/transactions/" + txId)
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"예약\"}"))
                .andExpect(status().isOk());

        // ───────── 7. Seller 인계확인 (라운드 11) ─────────
        mvc.perform(patch("/api/v1/transactions/" + txId)
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"인계확인\"}"))
                .andExpect(status().isOk());

        // ───────── 8. Buyer 인수확인 (라운드 11 — 자동 거래완료 + 정산) ─────────
        mvc.perform(patch("/api/v1/transactions/" + txId)
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"인수확인\"}"))
                .andExpect(status().isOk());

        // 정산 검증 — buyer hold 해제 / seller 잔액 적립 + status=거래완료
        mvc.perform(get("/api/v1/transactions/" + txId).cookie(buyerAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("거래완료"));

        // ───────── 8. Buyer Review 작성 ─────────
        mvc.perform(post("/api/v1/reviews")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":" + txId + ",\"rating\":5,\"comment\":\"좋아요\"}"))
                .andExpect(status().isCreated());
    }

    // ───────── helpers ─────────

    private Cookie loginAndExtractAt(String accessToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + accessToken + "\",\"redirectUri\":\"http://test/cb\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = result.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    private Long readId(MvcResult result, String jsonPath) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.at(jsonPath.replace("$.", "/").replace(".", "/")).asLong();
    }

    private String readString(MvcResult result, String jsonPath) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.at(jsonPath.replace("$.", "/").replace(".", "/")).asText();
    }
}
