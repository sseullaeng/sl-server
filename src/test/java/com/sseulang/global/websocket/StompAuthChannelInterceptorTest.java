package com.sseulang.global.websocket;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.chat.application.InMemoryFakeChatRoomRepository;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtClaims;
import com.sseulang.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthChannelInterceptorTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long OUTSIDER = 999L;

    private StompAuthChannelInterceptor interceptor;
    private JwtProvider jwtProvider;
    private AccessTokenBlacklist blacklist;
    private final Map<String, Long> validTokens = new java.util.HashMap<>();
    private Long roomId;

    @BeforeEach
    void setUp() {
        InMemoryFakeChatRoomRepository roomRepo = new InMemoryFakeChatRoomRepository();
        InMemoryFakeItemRepository itemRepo = new InMemoryFakeItemRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        ItemApplicationService itemSvc = new ItemApplicationService(itemRepo, catSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(), new com.sseulang.domain.item.application.NoOpWishlistView());
        ChatRoomApplicationService roomSvc = new ChatRoomApplicationService(roomRepo, itemSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.chat.application.NoOpUserView(), new com.sseulang.domain.chat.application.NoOpItemView());

        validTokens.clear();
        jwtProvider = mock(JwtProvider.class);
        when(jwtProvider.parse(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0, String.class);
            Long uid = validTokens.get(token);
            if (uid == null) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
            }
            return new JwtClaims(uid, "USER", "jti-" + token, null, Instant.now(), Instant.now().plusSeconds(60));
        });
        blacklist = mock(AccessTokenBlacklist.class);
        when(blacklist.isBlacklisted(anyString())).thenReturn(false);

        com.sseulang.domain.delivery.application.DeliveryApplicationService deliverySvc =
                org.mockito.Mockito.mock(com.sseulang.domain.delivery.application.DeliveryApplicationService.class);
        interceptor = new StompAuthChannelInterceptor(roomSvc, deliverySvc, jwtProvider, blacklist);

        Item item = itemRepo.save(Item.create(SELLER, null, "물건", "d", 1L, null, null, TradeType.판매, null));
        roomId = roomSvc.openFor(BUYER, item.getId()).id();
    }

    @Test
    @DisplayName("CONNECT 인증 누락_AUTH_TOKEN_MISSING")
    void connect_인증_누락() {
        Message<byte[]> msg = stompMessage(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_MISSING);
    }

    @Test
    @DisplayName("CONNECT 인증 정상_통과")
    void connect_인증_정상() {
        Message<byte[]> msg = stompMessage(StompCommand.CONNECT, null, BUYER);

        assertThatCode(() -> interceptor.preSend(msg, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUBSCRIBE /topic/chat-room/{id} 참여자_통과")
    void subscribe_참여자_통과() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, "/topic/chat-room/" + roomId, BUYER);

        assertThatCode(() -> interceptor.preSend(msg, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUBSCRIBE /topic/chat-room/{id} 외부인_CHAT_FORBIDDEN")
    void subscribe_외부인_거부() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, "/topic/chat-room/" + roomId, OUTSIDER);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
    }

    @Test
    @DisplayName("SUBSCRIBE 인증 누락_AUTH_TOKEN_MISSING")
    void subscribe_인증_누락() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, "/topic/chat-room/" + roomId, null);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_MISSING);
    }

    @Test
    @DisplayName("SUBSCRIBE /user/queue/... 별도 검증 X (Spring 자동)")
    void subscribe_user_queue_통과() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, "/user/queue/messages", BUYER);

        assertThatCode(() -> interceptor.preSend(msg, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUBSCRIBE /topic/chat-room/없는방_CHAT_ROOM_NOT_FOUND")
    void subscribe_없는_방() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, "/topic/chat-room/9999", BUYER);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("SUBSCRIBE /topic/chat-room/abc_INVALID_REQUEST (parse 실패)")
    void subscribe_잘못된_destination() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, "/topic/chat-room/abc", BUYER);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("SUBSCRIBE 화이트리스트 외 destination_FORBIDDEN")
    void subscribe_allowlist_외_거부() {
        // /topic/other 같은 임의 토픽
        Message<byte[]> other = stompMessage(StompCommand.SUBSCRIBE, "/topic/other", BUYER);
        assertThatThrownBy(() -> interceptor.preSend(other, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        // /queue/foo 직접 구독 시도
        Message<byte[]> queue = stompMessage(StompCommand.SUBSCRIBE, "/queue/foo", BUYER);
        assertThatThrownBy(() -> interceptor.preSend(queue, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("SUBSCRIBE null destination_FORBIDDEN")
    void subscribe_null_destination_거부() {
        Message<byte[]> msg = stompMessage(StompCommand.SUBSCRIBE, null, BUYER);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("SEND 커맨드_별도 검증 X (ApplicationService 책임)")
    void send_커맨드_통과() {
        Message<byte[]> msg = stompMessage(StompCommand.SEND, "/app/chat", BUYER);

        // SEND 는 인터셉터에서 검증 X
        Message<?> result = interceptor.preSend(msg, null);
        assertThat(result).isNotNull();
    }

    // ───────── follow-up #19 — native WS 토큰 인증 ─────────

    @Test
    @DisplayName("CONNECT Authorization Bearer 토큰_정상 검증 + accessor.setUser 주입")
    void connect_native_bearer_정상() {
        validTokens.put("good-token", BUYER);
        Message<byte[]> msg = stompMessageWithAuthHeader(StompCommand.CONNECT, "Bearer good-token");

        Message<?> result = interceptor.preSend(msg, null);

        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        Authentication auth = (Authentication) out.getUser();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(BUYER);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("CONNECT Authorization 헤더 없음_AUTH_TOKEN_MISSING")
    void connect_native_헤더없음() {
        Message<byte[]> msg = stompMessage(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_MISSING);
    }

    @Test
    @DisplayName("CONNECT Bearer prefix 누락_AUTH_TOKEN_MISSING")
    void connect_native_bearer_prefix_누락() {
        Message<byte[]> msg = stompMessageWithAuthHeader(StompCommand.CONNECT, "good-token");

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_MISSING);
    }

    @Test
    @DisplayName("CONNECT Bearer 빈 토큰_AUTH_TOKEN_MISSING")
    void connect_native_bearer_빈토큰() {
        Message<byte[]> msg = stompMessageWithAuthHeader(StompCommand.CONNECT, "Bearer    ");

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_MISSING);
    }

    @Test
    @DisplayName("CONNECT 위변조 토큰_AUTH_TOKEN_INVALID 그대로 전파")
    void connect_native_위변조() {
        Message<byte[]> msg = stompMessageWithAuthHeader(StompCommand.CONNECT, "Bearer forged-xx");

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("CONNECT blacklist 토큰_AUTH_TOKEN_REVOKED")
    void connect_native_blacklist() {
        validTokens.put("revoked-tk", BUYER);
        when(blacklist.isBlacklisted("jti-revoked-tk")).thenReturn(true);

        Message<byte[]> msg = stompMessageWithAuthHeader(StompCommand.CONNECT, "Bearer revoked-tk");

        assertThatThrownBy(() -> interceptor.preSend(msg, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
    }

    @Test
    @DisplayName("CONNECT handshake-Principal 우선_native bearer 무시")
    void connect_principal_우선() {
        // accessor.user 가 이미 채워져 있으면 native bearer 검증 skip
        Message<byte[]> msg = stompMessage(StompCommand.CONNECT, null, BUYER);

        Message<?> result = interceptor.preSend(msg, null);

        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        Authentication auth = (Authentication) out.getUser();
        assertThat(auth.getPrincipal()).isEqualTo(BUYER);
        // jwtProvider.parse 가 호출되지 않았어야 함 — 단, validTokens 는 비어 있어 호출되면 throw 났을 것.
    }

    private Message<byte[]> stompMessageWithAuthHeader(StompCommand cmd, String authValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(cmd);
        accessor.addNativeHeader("Authorization", authValue);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> stompMessage(StompCommand cmd, String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(cmd);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (userId != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
            ));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
