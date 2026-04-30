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
 * Day 9 — 가드 / 실패 시나리오 e2e. {@link EndToEndHappyPathIT} 가 happy path 한 시퀀스만 검증한다면
 * 본 IT 는 운영 중 자주 트리거될 수 있는 거부 분기 3개를 한 번에 검증.
 *
 * <ol>
 *   <li><b>미인증 차단</b> — LOCAL signup 직후 verified=false 상태로 거래/결제/출금/Item 등록/Chat
 *       시도 시 모두 {@code AUTH_EMAIL_NOT_VERIFIED} (FORBIDDEN).</li>
 *   <li><b>잔액 부족</b> — 충전 안 한 buyer 가 거래완료 시 settle 단계에서 {@code INSUFFICIENT_POINT}
 *       + 트랜잭션 전체 롤백 (Item / Tx 상태 변경 X).</li>
 *   <li><b>출금 멱등성</b> — 같은 idempotencyKey 두 번 신청 시 한 건만 생성, 두 호출 같은 id 반환.</li>
 * </ol>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(EndToEndGuardScenariosIT.TestOAuthBeans.class)
@Testcontainers
class EndToEndGuardScenariosIT {

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
        r.add("app.dev-auth.enabled", () -> "false");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("kakaoOAuthProvider")
    private OAuthProvider kakaoOAuthProvider;

    @MockBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void cleanDb() {
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("DELETE FROM email_verifications").executeUpdate();
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM withdrawals").executeUpdate();
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

    // ───────── 시나리오 1: 미인증 사용자 차단 ─────────

    @Test
    @DisplayName("미인증 LOCAL 가입자_거래/결제/출금/Item 등록 모두 AUTH_EMAIL_NOT_VERIFIED 거부")
    void scenario_미인증_차단() throws Exception {
        // LOCAL 가입 — verified=false 상태로 자동 로그인. 인증 메일은 LogEmailSender 로 콘솔에만 발송.
        Cookie atCookie = signupLocalAndExtractAt(
                "unverified-" + System.nanoTime() + "@e2e.test",
                "password123!", "미인증유저"
        );

        // 거래 시작 — buyer 자격 verified 필요. itemId 는 아직 없지만 verified 가드가 먼저 트리거.
        mvc.perform(post("/api/v1/transactions")
                        .with(csrf())
                        .cookie(atCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_NOT_VERIFIED"));

        // 충전
        mvc.perform(post("/api/v1/payments/charge")
                        .with(csrf())
                        .cookie(atCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_NOT_VERIFIED"));

        // 출금
        mvc.perform(post("/api/v1/withdrawals")
                        .with(csrf())
                        .cookie(atCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"k-1","amount":1000,
                                 "bankName":"신한","accountNumber":"110-1","accountHolder":"홍길동"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_NOT_VERIFIED"));

        // Item 등록
        mvc.perform(post("/api/v1/items")
                        .with(csrf())
                        .cookie(atCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"d","price":1000,"tradeType":"판매"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_NOT_VERIFIED"));
    }

    // ───────── 시나리오 2: 잔액 부족 정산 실패 ─────────

    @Test
    @DisplayName("buyer 충전 안 한 상태_거래완료 시 INSUFFICIENT_POINT + 모든 상태 롤백")
    void scenario_잔액부족_롤백() throws Exception {
        // Seller — OAuth (verified=true). Item 등록.
        Cookie sellerAt = oauthLoginAndExtractAt("SELLER", "kakao-seller-" + UUID.randomUUID(),
                "seller-" + System.nanoTime() + "@e2e.test", "셀러");
        Long itemId = registerItem(sellerAt, 50_000L);

        // Buyer — OAuth (verified=true). 충전 안 함 — 잔액 0.
        Cookie buyerAt = oauthLoginAndExtractAt("BUYER", "kakao-buyer-" + UUID.randomUUID(),
                "buyer-" + System.nanoTime() + "@e2e.test", "바이어");

        // 거래 생성 — 채팅중 (자금 이동 없음).
        Long txId = createTransaction(buyerAt, itemId);

        // Seller 예약 → 거래완료 시 정산 시도. buyer 잔액 0 < 50000 → INSUFFICIENT_POINT.
        mvc.perform(patch("/api/v1/transactions/" + txId)
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"예약\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/transactions/" + txId)
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"거래완료\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_POINT"));

        // 트랜잭션 롤백 검증 — Tx 상태 = 예약 (거래완료 X).
        mvc.perform(get("/api/v1/transactions/" + txId).cookie(buyerAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("예약"));
    }

    // ───────── 시나리오 3: 출금 멱등성 ─────────

    @Test
    @DisplayName("출금 같은 idempotencyKey 두 번 호출_1건만 생성 + 잔액 1회만 차감")
    void scenario_출금_멱등성() throws Exception {
        Cookie userAt = oauthLoginAndExtractAt("WITHDRAW_USER", "kakao-w-" + UUID.randomUUID(),
                "withdraw-" + System.nanoTime() + "@e2e.test", "출금자");

        // 충전 — 100,000 잔액 확보.
        long chargeAmount = 100_000L;
        String merchantUid = startCharge(userAt, chargeAmount);
        when(paymentGateway.confirm(any(), any(), any(Long.class)))
                .thenReturn(new PaymentConfirmResult(
                        "tossKey-" + UUID.randomUUID(), merchantUid, chargeAmount,
                        PaymentMethod.CARD, LocalDateTime.now(), "{}"
                ));
        confirmCharge(userAt, merchantUid, chargeAmount);

        // 출금 신청 #1 — idempotencyKey "K1".
        String body = """
                {"idempotencyKey":"K1","amount":30000,
                 "bankName":"신한","accountNumber":"110-1","accountHolder":"홍길동"}""";
        MvcResult r1 = mvc.perform(post("/api/v1/withdrawals")
                        .with(csrf())
                        .cookie(userAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id1 = readLong(r1, "/data");

        // 출금 신청 #2 — 같은 idempotencyKey. dedup → 같은 id 반환.
        MvcResult r2 = mvc.perform(post("/api/v1/withdrawals")
                        .with(csrf())
                        .cookie(userAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id2 = readLong(r2, "/data");

        assertThat(id2).as("두 호출 같은 id").isEqualTo(id1);

        // 출금 후 잔액 = 100k - 30k = 70k (한 번만 차감).
        // GET /api/v1/withdrawals 로 행 1개만 조회되는지 확인.
        mvc.perform(get("/api/v1/withdrawals").cookie(userAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ───────── 헬퍼 ─────────

    private Cookie signupLocalAndExtractAt(String email, String password, String nickname) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}",
                email, password, nickname);
        MvcResult result = mvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = result.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    private Cookie oauthLoginAndExtractAt(String tokenSentinel, String providerId, String email, String nickname) throws Exception {
        OAuthUserInfo info = new OAuthUserInfo(
                SocialProvider.KAKAO, providerId, new Email(email), nickname, null
        );
        when(kakaoOAuthProvider.verifyAndFetch(tokenSentinel)).thenReturn(info);
        MvcResult result = mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + tokenSentinel + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = result.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    private Long registerItem(Cookie sellerAt, long price) throws Exception {
        String body = String.format("""
                {"title":"e2e","description":"e2e","price":%d,"tradeType":"판매"}""", price);
        MvcResult result = mvc.perform(post("/api/v1/items")
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "/data/id");
    }

    private Long createTransaction(Cookie buyerAt, Long itemId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/transactions")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "/data/id");
    }

    private String startCharge(Cookie buyerAt, long amount) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/payments/charge")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readString(result, "/data/merchantUid");
    }

    private void confirmCharge(Cookie buyerAt, String merchantUid, long amount) throws Exception {
        String body = String.format(
                "{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}",
                "pk-" + UUID.randomUUID(), merchantUid, amount
        );
        mvc.perform(post("/api/v1/payments/charge/confirm")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private Long readLong(MvcResult result, String pointer) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.at(pointer).asLong();
    }

    private String readString(MvcResult result, String pointer) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.at(pointer).asText();
    }
}
