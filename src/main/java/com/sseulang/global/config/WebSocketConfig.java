package com.sseulang.global.config;

import com.sseulang.global.websocket.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket — 가이드 §4.10 실시간 채팅·알림 영역.
 *
 * <p>Origin 화이트리스트는 {@link CorsProperties} 의 {@code app.cors.allowed-origins} 사용 — REST CORS 와 동일.
 * Codex 게이트 1 보강: 와일드카드 Origin 제거 (운영 위험).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(
            StompAuthChannelInterceptor authInterceptor,
            CorsProperties corsProperties
    ) {
        this.authInterceptor = authInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOrigins(corsProperties.originsArray())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 단순 인메모리 broker — 5/6 이후 부하 증가 시 외부 broker(Redis/RabbitMQ) 검토
        registry.enableSimpleBroker("/topic", "/queue", "/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
