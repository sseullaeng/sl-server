package com.sseulang.global.websocket;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtClaims;
import com.sseulang.global.security.JwtProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP CONNECT/SUBSCRIBE 단계의 인증·인가 검증. 가이드 §4.10 / §9.6 (게이트 1 영역).
 *
 * <ul>
 *   <li>CONNECT: Principal 없으면 STOMP nativeHeader {@code Authorization: Bearer <jwt>} 에서
 *       토큰을 추출해 {@link JwtProvider} 로 검증 → {@link AccessTokenBlacklist} 체크 후
 *       accessor.setUser 로 SecurityContext 와 동등한 Principal 주입 (follow-up #19).
 *       쿠키 기반 SockJS 클라이언트는 handshake 단계에서 이미 SecurityContext 가 채워져
 *       getUser() 가 non-null. native WS(쿠키 X) 클라이언트는 이 헤더 경로로만 인증.
 *       어떤 방법으로도 Principal 못 채우면 {@link ErrorCode#AUTH_TOKEN_MISSING}.</li>
 *   <li>SUBSCRIBE — destination allowlist (Codex 게이트 1 보강):
 *     <ul>
 *       <li>{@code /topic/chat-room/{roomId}} — 채팅방 참여자만</li>
 *       <li>{@code /user/queue/messages}, {@code /user/queue/notifications} — Spring 자동 본인 라우팅</li>
 *       <li>그 외 destination — {@link ErrorCode#FORBIDDEN}</li>
 *     </ul>
 *   </li>
 *   <li>SEND: ApplicationService 가 권한 검증 책임 — 본 인터셉터 패스.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat-room/";
    private static final String USER_QUEUE_MESSAGES = "/user/queue/messages";
    private static final String USER_QUEUE_NOTIFICATIONS = "/user/queue/notifications";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ChatRoomApplicationService chatRoomService;
    private final JwtProvider jwtProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;

    public StompAuthChannelInterceptor(
            ChatRoomApplicationService chatRoomService,
            JwtProvider jwtProvider,
            AccessTokenBlacklist accessTokenBlacklist
    ) {
        this.chatRoomService = chatRoomService;
        this.jwtProvider = jwtProvider;
        this.accessTokenBlacklist = accessTokenBlacklist;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        switch (command) {
            case CONNECT -> {
                requireAuthenticated(accessor);
                // accessor.setUser 로 채워진 Principal 을 후속 채널로 전파하려면 새 Message 로 재발행 필요.
                // 기존 message 의 header 는 immutable 라 accessor 변경이 그대로 반영되지 않음.
                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }
            case SUBSCRIBE -> requireSubscribePermission(accessor);
            default -> { /* 다른 커맨드는 ApplicationService 검증 위임 */ }
        }
        return message;
    }

    private void requireAuthenticated(StompHeaderAccessor accessor) {
        // 1. handshake 단계에서 SecurityContext propagation 으로 이미 채워진 경우 통과 (SockJS+쿠키)
        if (extractUserIdOrNull(accessor) != null) {
            return;
        }
        // 2. native WS 클라이언트 — STOMP nativeHeader Authorization: Bearer <jwt> 로 인증
        String bearer = firstNativeHeader(accessor, AUTH_HEADER);
        if (bearer == null || !bearer.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING);
        }
        String token = bearer.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING);
        }
        JwtClaims claims = jwtProvider.parse(token);  // EXPIRED / INVALID 는 그대로 BusinessException
        if (accessTokenBlacklist.isBlacklisted(claims.jti())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(
                claims.userId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
        );
        accessor.setUser(auth);
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private void requireSubscribePermission(StompHeaderAccessor accessor) {
        Long userId = extractUserIdOrNull(accessor);
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING);
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (destination.startsWith(CHAT_TOPIC_PREFIX)) {
            chatRoomService.requireParticipant(parseRoomId(destination), userId);
            return;
        }
        if (destination.equals(USER_QUEUE_MESSAGES) || destination.equals(USER_QUEUE_NOTIFICATIONS)) {
            // Spring UserDestinationMessageHandler 가 본인 destination 으로 자동 라우팅 — 통과.
            return;
        }
        // 화이트리스트 외 destination 거부 — 향후 새 토픽 추가 시 본 메서드에 명시 추가 필요.
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private static Long parseRoomId(String destination) {
        try {
            return Long.parseLong(destination.substring(CHAT_TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static Long extractUserIdOrNull(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication auth)) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof Long ? (Long) principal : null;
    }
}
