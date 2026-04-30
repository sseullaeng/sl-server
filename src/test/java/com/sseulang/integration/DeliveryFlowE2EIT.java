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
 * 배달대행 e2e — 요청 등록 → 라이더 수락 → 픽업 → 배송 → 정산 한 시퀀스 + race / self-accept.
 *
 * <p>외부 의존(OAuth/Toss) 만 mock. DB·Redis·Mongo·Spring Security 모두 real.</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(DeliveryFlowE2EIT.TestOAuthBeans.class)
@Testcontainers
class DeliveryFlowE2EIT {

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
            em.createNativeQuery("DELETE FROM point_histories").executeUpdate();
            em.createNativeQuery("DELETE FROM payments").executeUpdate();
            em.createNativeQuery("DELETE FROM deliveries").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("delivery happy path — 요청 → 수락 → 픽업 → 배송 → 정산 → 잔액 이동")
    void delivery_happy_path() throws Exception {
        // 요청자 가입 + 충전
        Cookie requesterAt = signup("kakao-req-" + UUID.randomUUID(), "REQ_TOKEN", "요청자");
        long chargeAmount = 50_000L;
        chargeBalance(requesterAt, chargeAmount);

        // 라이더 가입
        Cookie riderAt = signup("kakao-rider-" + UUID.randomUUID(), "RIDER_TOKEN", "라이더");

        // 1) 요청 등록
        long fee = 5_000L;
        String createBody = String.format("""
                {
                  "pickupAddress": "서울 강남구 테헤란로 123",
                  "dropoffAddress": "서울 송파구 올림픽로 456",
                  "itemDescription": "A4 서류 봉투 1개",
                  "fee": %d,
                  "requestedDeadline": "%s",
                  "memo": "1층 로비"
                }
                """, fee, LocalDateTime.now().plusHours(2));
        MvcResult created = mvc.perform(post("/api/v1/deliveries")
                        .with(csrf()).cookie(requesterAt)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("모집중"))
                .andReturn();
        Long deliveryId = readId(created, "/data/id");
        assertThat(deliveryId).isNotNull();

        // 2) 라이더 수락
        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/accept")
                        .with(csrf()).cookie(riderAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("수락"))
                .andExpect(jsonPath("$.data.riderId").exists());

        // 3) 픽업
        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/pickup")
                        .with(csrf()).cookie(riderAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("배송중"));

        // 4) 배송 완료
        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/deliver")
                        .with(csrf()).cookie(riderAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("배송완료"));

        // 5) 요청자 정산 확인 → 포인트 이동
        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/complete")
                        .with(csrf()).cookie(requesterAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("정산완료"));

        // 잔액 검증 — 요청자: 50000-5000=45000, 라이더: 0+5000=5000.
        // 일반 user 엔드포인트 미존재 → 본 IT 는 DB 로 직접 조회.
        long requesterBalance = sumBalanceForRequester();
        long riderBalance = sumBalanceForRider();
        assertThat(requesterBalance).isEqualTo(45_000L);
        assertThat(riderBalance).isEqualTo(5_000L);

        // PointHistory 두 건 적재 검증 (배달결제 / 배달정산)
        Number historyCount = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM point_histories WHERE reference_type = 'DELIVERY' AND reference_id = " + deliveryId
        ).getSingleResult();
        assertThat(historyCount.intValue()).isEqualTo(2);
    }

    private long sumBalanceForRequester() {
        return ((Number) em.createNativeQuery(
                "SELECT point_balance FROM users WHERE id = " +
                "(SELECT requester_id FROM deliveries ORDER BY id DESC LIMIT 1)"
        ).getSingleResult()).longValue();
    }

    private long sumBalanceForRider() {
        return ((Number) em.createNativeQuery(
                "SELECT point_balance FROM users WHERE id = " +
                "(SELECT rider_id FROM deliveries ORDER BY id DESC LIMIT 1)"
        ).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("accept race — 이미 수락된 요청에 다른 라이더 시도 시 409 ALREADY_ACCEPTED")
    void accept_race() throws Exception {
        Cookie req = signup("kakao-req2-" + UUID.randomUUID(), "REQ2", "요청자2");
        Cookie r1 = signup("kakao-r1-" + UUID.randomUUID(), "R1", "라이더1");
        Cookie r2 = signup("kakao-r2-" + UUID.randomUUID(), "R2", "라이더2");

        Long deliveryId = createDelivery(req, 3000L);

        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/accept")
                        .with(csrf()).cookie(r1))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/accept")
                        .with(csrf()).cookie(r2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("self-accept 차단 — 본인 등록 요청 수락 시 400 SELF_NOT_ALLOWED")
    void accept_self_blocked() throws Exception {
        Cookie req = signup("kakao-self-" + UUID.randomUUID(), "SELF", "본인");

        Long deliveryId = createDelivery(req, 2000L);

        mvc.perform(patch("/api/v1/deliveries/" + deliveryId + "/accept")
                        .with(csrf()).cookie(req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_SELF_NOT_ALLOWED"));
    }

    // ───────── helpers ─────────

    private Cookie signup(String providerId, String accessToken, String nickname) throws Exception {
        OAuthUserInfo info = new OAuthUserInfo(
                SocialProvider.KAKAO, providerId,
                new Email("u-" + System.nanoTime() + "@d.test"),
                nickname, null
        );
        when(kakaoOAuthProvider.supports()).thenReturn(SocialProvider.KAKAO);
        when(kakaoOAuthProvider.verifyAndFetch(accessToken)).thenReturn(info);

        MvcResult result = mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = result.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    private void chargeBalance(Cookie at, long amount) throws Exception {
        MvcResult chargeStart = mvc.perform(post("/api/v1/payments/charge")
                        .with(csrf()).cookie(at)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String merchantUid = readString(chargeStart, "/data/merchantUid");

        when(paymentGateway.confirm(any(), any(), any(Long.class)))
                .thenReturn(new PaymentConfirmResult(
                        "pk-" + UUID.randomUUID(), merchantUid, amount, PaymentMethod.CARD,
                        LocalDateTime.now(), "{\"approved\":true}"
                ));

        String confirmBody = String.format(
                "{\"paymentKey\":\"pk-%s\",\"orderId\":\"%s\",\"amount\":%d}",
                UUID.randomUUID(), merchantUid, amount
        );
        mvc.perform(post("/api/v1/payments/charge/confirm")
                        .with(csrf()).cookie(at)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk());
    }

    private Long createDelivery(Cookie requester, long fee) throws Exception {
        String body = String.format("""
                {
                  "pickupAddress": "서울 강남구",
                  "dropoffAddress": "서울 송파구",
                  "itemDescription": "서류",
                  "fee": %d,
                  "requestedDeadline": "%s"
                }
                """, fee, LocalDateTime.now().plusHours(1));
        MvcResult created = mvc.perform(post("/api/v1/deliveries")
                        .with(csrf()).cookie(requester)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(created, "/data/id");
    }

    private Long readId(MvcResult result, String pointer) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.at(pointer).asLong();
    }

    private String readString(MvcResult result, String pointer) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.at(pointer).asText();
    }
}
