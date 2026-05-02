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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * Day 9 — admin 운영 흐름 e2e.
 *
 * <ol>
 *   <li>admin 로그인 → ADMIN cookie</li>
 *   <li>일반 user OAuth signup → admin 회원 목록에 노출</li>
 *   <li>admin user 차단 → 차단된 user OAuth 재로그인 시 USER_BLOCKED</li>
 *   <li>user 충전 + 출금 신청 → admin 출금 목록 조회 → 승인 → 완료 상태 전이</li>
 *   <li>admin stats dashboard → 누적 카운트 응답</li>
 * </ol>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(AdminFlowE2EIT.TestOAuthBeans.class)
@Testcontainers
class AdminFlowE2EIT {

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

    private static final String ADMIN_PASSWORD = "admin-test-password";

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
            em.createNativeQuery("DELETE FROM withdrawals").executeUpdate();
            em.createNativeQuery("DELETE FROM payments").executeUpdate();
            em.createNativeQuery("DELETE FROM email_verifications").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            em.createNativeQuery("DELETE FROM admins").executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("admin 로그인 → 회원 차단 → 차단된 user OAuth 재로그인 거부 (USER_BLOCKED)")
    void admin_user_차단_흐름() throws Exception {
        seedAdmin("admin1");
        Cookie adminAt = adminLogin("admin1");

        // 일반 user OAuth signup
        String victimEmail = "victim-" + System.nanoTime() + "@e2e.test";
        String victimProviderId = "kakao-victim-" + UUID.randomUUID();
        Cookie victimAt = oauthLogin("VICTIM_TOKEN", victimProviderId, victimEmail, "victim");
        Long victimId = userIdFromMe(victimAt);

        // admin 회원 목록에 victim 노출
        mvc.perform(get("/api/v1/admin/users").cookie(adminAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // admin 차단
        mvc.perform(patch("/api/v1/admin/users/" + victimId + "/block")
                        .with(csrf())
                        .cookie(adminAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isOk());

        // 차단된 user OAuth 재로그인 시도 → USER_BLOCKED.
        // 같은 (provider, providerId) 로 재진입해야 findOrCreateBySocial 가 기존 user 매칭 → user.isBlocked() 체크.
        OAuthUserInfo info = new OAuthUserInfo(
                SocialProvider.KAKAO, victimProviderId,
                new Email(victimEmail), "victim", null
        );
        when(kakaoOAuthProvider.exchangeCodeAndFetch("VICTIM_RELOGIN", "http://test/cb")).thenReturn(info);
        mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"VICTIM_RELOGIN\",\"redirectUri\":\"http://test/cb\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_BLOCKED"));
    }

    @Test
    @DisplayName("user 출금 신청 → admin 승인 → 완료 상태 전이")
    void admin_withdrawal_승인_완료_흐름() throws Exception {
        seedAdmin("admin2");
        Cookie adminAt = adminLogin("admin2");

        // user 충전 + 출금 신청
        String userEmail = "user-" + System.nanoTime() + "@e2e.test";
        Cookie userAt = oauthLogin("USER_TOKEN", "kakao-user-" + UUID.randomUUID(), userEmail, "user");
        long chargeAmount = 100_000L;
        String merchantUid = startCharge(userAt, chargeAmount);
        when(paymentGateway.confirm(any(), any(), any(Long.class)))
                .thenReturn(new PaymentConfirmResult(
                        "tossKey-" + UUID.randomUUID(), merchantUid, chargeAmount,
                        PaymentMethod.CARD, LocalDateTime.now(), "{}"
                ));
        confirmCharge(userAt, merchantUid, chargeAmount);

        Long withdrawalId = requestWithdrawal(userAt, 30_000L);

        // admin 신청 상태 목록 조회 — 1건
        mvc.perform(get("/api/v1/admin/withdrawals").param("status", "신청").cookie(adminAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // admin 승인
        mvc.perform(patch("/api/v1/admin/withdrawals/" + withdrawalId)
                        .with(csrf())
                        .cookie(adminAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"memo\":\"확인 완료\"}"))
                .andExpect(status().isOk());

        // admin 완료 처리 (외부 이체 mock)
        mvc.perform(patch("/api/v1/admin/withdrawals/" + withdrawalId)
                        .with(csrf())
                        .cookie(adminAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"COMPLETE\"}"))
                .andExpect(status().isOk());

        // user 출금 상세 → status=완료
        mvc.perform(get("/api/v1/withdrawals/" + withdrawalId).cookie(userAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("완료"));
    }

    @Test
    @DisplayName("admin stats dashboard — 4개 도메인 카운트 응답")
    void admin_stats_dashboard() throws Exception {
        seedAdmin("admin3");
        Cookie adminAt = adminLogin("admin3");

        // 시드 — user 1, withdrawal 1
        Cookie userAt = oauthLogin("DASH_TOKEN", "kakao-dash-" + UUID.randomUUID(),
                "dash-" + System.nanoTime() + "@e2e.test", "dashUser");
        long chargeAmount = 50_000L;
        String merchantUid = startCharge(userAt, chargeAmount);
        when(paymentGateway.confirm(any(), any(), any(Long.class)))
                .thenReturn(new PaymentConfirmResult(
                        "tossKey-" + UUID.randomUUID(), merchantUid, chargeAmount,
                        PaymentMethod.CARD, LocalDateTime.now(), "{}"
                ));
        confirmCharge(userAt, merchantUid, chargeAmount);
        requestWithdrawal(userAt, 10_000L);

        mvc.perform(get("/api/v1/admin/stats/dashboard").cookie(adminAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.total").value(1))
                .andExpect(jsonPath("$.data.users.active").value(1))
                .andExpect(jsonPath("$.data.payments.paidCount").value(1))
                .andExpect(jsonPath("$.data.payments.totalPaidAmount").value(50000))
                .andExpect(jsonPath("$.data.withdrawals.total").value(1));
    }

    // ───────── 헬퍼 ─────────

    private void seedAdmin(String username) {
        String hashed = new BCryptPasswordEncoder().encode(ADMIN_PASSWORD);
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("""
                    INSERT INTO admins (username, password, name, role, is_active)
                    VALUES (?, ?, ?, ?, ?)
                    """)
                    .setParameter(1, username)
                    .setParameter(2, hashed)
                    .setParameter(3, "관리자")
                    .setParameter(4, "ADMIN")
                    .setParameter(5, true)
                    .executeUpdate();
            return null;
        });
    }

    private Cookie adminLogin(String username) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, ADMIN_PASSWORD);
        MvcResult r = mvc.perform(post("/api/v1/auth/admin/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = r.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    private Cookie oauthLogin(String tokenSentinel, String providerId, String email, String nickname) throws Exception {
        OAuthUserInfo info = new OAuthUserInfo(
                SocialProvider.KAKAO, providerId, new Email(email), nickname, null
        );
        when(kakaoOAuthProvider.exchangeCodeAndFetch(tokenSentinel, "http://test/cb")).thenReturn(info);
        MvcResult r = mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + tokenSentinel + "\",\"redirectUri\":\"http://test/cb\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = r.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    /**
     * user 자기 자신 id 추출 — admin user 목록 응답에서 첫 번째 row 의 id 사용 (시나리오 시드 1명만).
     */
    private Long userIdFromMe(Cookie userAt) throws Exception {
        // /api/v1/admin/users 가 가장 단순한 경로지만 admin 권한 필요. 본 시나리오에선 admin 호출 후 추출.
        // 단순화: 출금 신청용으로 만든 dummy 호출 대신, native SELECT.
        Object id = new TransactionTemplate(txManager).execute(s ->
                em.createNativeQuery("SELECT id FROM users ORDER BY id DESC LIMIT 1").getSingleResult()
        );
        return ((Number) id).longValue();
    }

    private String startCharge(Cookie userAt, long amount) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/payments/charge")
                        .with(csrf())
                        .cookie(userAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        return root.at("/data/merchantUid").asText();
    }

    private void confirmCharge(Cookie userAt, String merchantUid, long amount) throws Exception {
        String body = String.format(
                "{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}",
                "pk-" + UUID.randomUUID(), merchantUid, amount
        );
        mvc.perform(post("/api/v1/payments/charge/confirm")
                        .with(csrf())
                        .cookie(userAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private Long requestWithdrawal(Cookie userAt, long amount) throws Exception {
        String body = String.format("""
                {"idempotencyKey":"%s","amount":%d,"bankName":"신한","accountNumber":"110-1","accountHolder":"홍길동"}""",
                "k-" + UUID.randomUUID(), amount);
        MvcResult r = mvc.perform(post("/api/v1/withdrawals")
                        .with(csrf())
                        .cookie(userAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        return root.at("/data").asLong();
    }
}
