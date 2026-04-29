package com.sseulang.global.websocket;

import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT/SUBSCRIBE 단계의 인증·인가 검증. 가이드 §4.10 / §9.6 (게이트 1 영역).
 *
 * <ul>
 *   <li>CONNECT: Principal 없으면 {@link ErrorCode#AUTH_TOKEN_MISSING}.</li>
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

    private final ChatRoomApplicationService chatRoomService;

    public StompAuthChannelInterceptor(ChatRoomApplicationService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        switch (command) {
            case CONNECT -> requireAuthenticated(accessor);
            case SUBSCRIBE -> requireSubscribePermission(accessor);
            default -> { /* 다른 커맨드는 ApplicationService 검증 위임 */ }
        }
        return message;
    }

    private void requireAuthenticated(StompHeaderAccessor accessor) {
        if (extractUserIdOrNull(accessor) == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING);
        }
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
