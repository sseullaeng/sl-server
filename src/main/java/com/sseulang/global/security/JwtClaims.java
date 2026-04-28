package com.sseulang.global.security;

import java.time.Instant;

/**
 * JWT 파싱 결과. AccessToken 의 경우 role 채워짐, RefreshToken 의 경우 role 은 null.
 */
public record JwtClaims(
        Long userId,
        String role,
        String jti,
        Instant issuedAt,
        Instant expiresAt
) {
}
