package com.sseulang.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds,
        long refreshTokenValiditySeconds
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.jwt.secret 은 32바이트 이상이어야 합니다");
        }
        if (accessTokenValiditySeconds <= 0 || refreshTokenValiditySeconds <= 0) {
            throw new IllegalArgumentException("토큰 유효기간은 양수여야 합니다");
        }
    }
}
