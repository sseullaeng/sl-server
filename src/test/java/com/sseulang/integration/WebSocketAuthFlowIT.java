package com.sseulang.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day 9 — STOMP WebSocket 인증 e2e. 실제 SockJS 핸드셰이크에 cookie 첨부 → CONNECT 시 ChannelInterceptor
 * 가 SecurityContext 의 user 를 STOMP session 에 전파하는지, SUBSCRIBE 권한 가드가 정확히 동작하는지 검증.
 *
 * <ol>
 *   <li>유효 cookie buyer → CONNECT 성공 → 본인이 만든 채팅방 SUBSCRIBE 성공</li>
 *   <li>cookie 없는 익명 → CONNECT 실패</li>
 *   <li>유효 cookie outsider → 다른 사용자 채팅방 SUBSCRIBE 실패 (CHAT_FORBIDDEN)</li>
 * </ol>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
@Import(WebSocketAuthFlowIT.TestOAuthBeans.class)
@Testcontainers
class WebSocketAuthFlowIT {

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
        // CORS — SockJS handshake 요청에서 Origin 헤더 일치 필요. 테스트에선 CORS 검증 우회 위해 wildcard 허용.
        r.add("app.cors.allowed-origins", () -> "http://localhost");
    }

    @LocalServerPort private int port;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("kakaoOAuthProvider")
    private OAuthProvider kakaoOAuthProvider;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        // messages/notifications 는 MongoDB 저장 — 본 IT 는 SUBSCRIBE 만 검증, 메시지 미발송이라 cleanup 생략.
        new TransactionTemplate(txManager).execute(s -> {
            em.createNativeQuery("DELETE FROM chat_rooms").executeUpdate();
            em.createNativeQuery("DELETE FROM item_images").executeUpdate();
            em.createNativeQuery("DELETE FROM item_hashtags").executeUpdate();
            em.createNativeQuery("DELETE FROM items").executeUpdate();
            em.createNativeQuery("DELETE FROM email_verifications").executeUpdate();
            em.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });

        // SockJS 클라이언트 — server 가 .withSockJS() 로 등록한 endpoint 에 connect 가능.
        StandardWebSocketClient ws = new StandardWebSocketClient();
        SockJsClient sockJs = new SockJsClient(List.of((Transport) new WebSocketTransport(ws)));
        stompClient = new WebSocketStompClient(sockJs);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("유효 cookie buyer_CONNECT + 본인 채팅방 SUBSCRIBE 성공")
    void valid_cookie_subscribe_success() throws Exception {
        // seller (Item 등록 가능) + buyer (채팅방 생성 가능) — 둘 다 verified=true (OAuth)
        Cookie sellerAt = oauthLogin("SELLER_TOKEN", "kakao-seller-" + UUID.randomUUID(),
                "seller-" + System.nanoTime() + "@e2e.test", "seller");
        Long itemId = registerItem(sellerAt);
        Cookie buyerAt = oauthLogin("BUYER_TOKEN", "kakao-buyer-" + UUID.randomUUID(),
                "buyer-" + System.nanoTime() + "@e2e.test", "buyer");
        Long roomId = openChatRoom(buyerAt, itemId);

        StompSession session = connectWithCookie(buyerAt);
        try {
            // 본인 참여 채팅방 SUBSCRIBE — interceptor 의 requireParticipant 통과
            session.subscribe("/topic/chat-room/" + roomId, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) { }
            });
            // SUBSCRIBE 가 ERROR frame 없이 처리되면 성공 — 짧게 대기 후 session 활성 확인
            Thread.sleep(200);
            assertThat(session.isConnected()).isTrue();
        } finally {
            session.disconnect();
        }
    }

    @Test
    @DisplayName("cookie 없는 익명_CONNECT 실패 (interceptor 가 AUTH_TOKEN_MISSING)")
    void anonymous_connect_fails() {
        // cookie 없이 핸드셰이크 → SecurityContext 비어있음 → STOMP CONNECT 시 interceptor 거부 → ConnectionLost
        WebSocketHttpHeaders empty = new WebSocketHttpHeaders();
        assertThatThrownBy(() -> stompClient
                .connectAsync(wsUrl(), empty, new StompHeaders(), new StompSessionHandlerAdapter() {})
                .get(3, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    @DisplayName("외부인_다른 사용자 채팅방 SUBSCRIBE 거부 (CHAT_FORBIDDEN)")
    void outsider_subscribe_forbidden() throws Exception {
        Cookie sellerAt = oauthLogin("SELLER_TOKEN", "kakao-seller-" + UUID.randomUUID(),
                "seller-" + System.nanoTime() + "@e2e.test", "seller");
        Long itemId = registerItem(sellerAt);
        Cookie buyerAt = oauthLogin("BUYER_TOKEN", "kakao-buyer-" + UUID.randomUUID(),
                "buyer-" + System.nanoTime() + "@e2e.test", "buyer");
        Long roomId = openChatRoom(buyerAt, itemId);
        // 제3자 outsider 가 buyer↔seller 채팅방 SUBSCRIBE 시도
        Cookie outsiderAt = oauthLogin("OUT_TOKEN", "kakao-out-" + UUID.randomUUID(),
                "out-" + System.nanoTime() + "@e2e.test", "outsider");

        StompSession session = connectWithCookie(outsiderAt);
        try {
            // SUBSCRIBE — interceptor 가 CHAT_FORBIDDEN 으로 거부 → ERROR frame → session 끊김
            session.subscribe("/topic/chat-room/" + roomId, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) { }
            });
            // ERROR frame 처리에 짧은 시간 필요. interceptor 거부 후 session 종료 확인.
            Thread.sleep(500);
            assertThat(session.isConnected())
                    .as("외부인 SUBSCRIBE → interceptor ERROR frame → session 종료 기대")
                    .isFalse();
        } finally {
            if (session.isConnected()) session.disconnect();
        }
    }

    // ───────── 헬퍼 ─────────

    private String wsUrl() {
        return "http://localhost:" + port + "/ws-stomp";
    }

    private StompSession connectWithCookie(Cookie atCookie) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", atCookie.getName() + "=" + atCookie.getValue());
        return stompClient
                .connectAsync(wsUrl(), headers, new StompHeaders(), new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private Cookie oauthLogin(String tokenSentinel, String providerId, String email, String nickname) throws Exception {
        OAuthUserInfo info = new OAuthUserInfo(
                SocialProvider.KAKAO, providerId, new Email(email), nickname, null
        );
        when(kakaoOAuthProvider.verifyAndFetch(tokenSentinel)).thenReturn(info);
        MvcResult r = mvc.perform(post("/api/v1/auth/oauth2/kakao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + tokenSentinel + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.ACCESS_TOKEN_COOKIE))
                .andReturn();
        Cookie at = r.getResponse().getCookie(CookieUtil.ACCESS_TOKEN_COOKIE);
        assertThat(at).isNotNull();
        return at;
    }

    private Long registerItem(Cookie sellerAt) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/items")
                        .with(csrf())
                        .cookie(sellerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"e2e\",\"description\":\"e2e\",\"price\":10000,\"tradeType\":\"판매\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        return root.at("/data/id").asLong();
    }

    private Long openChatRoom(Cookie buyerAt, Long itemId) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/chat-rooms")
                        .with(csrf())
                        .cookie(buyerAt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        return root.at("/data/id").asLong();
    }
}
