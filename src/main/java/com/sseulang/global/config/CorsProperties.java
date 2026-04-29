package com.sseulang.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** {@code app.cors.allowed-origins} — REST CORS + WebSocket handshake Origin 화이트리스트 공통. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public String[] originsArray() {
        return allowedOrigins.toArray(String[]::new);
    }
}
