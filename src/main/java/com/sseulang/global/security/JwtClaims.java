package com.sseulang.global.security;

import java.time.Instant;

/**
 * JWT 파싱 결과. AT/RT 모두 issue 시 role 클레임이 포함되므로 parse 시 role 채워진다
 * (RT 도 user/admin 격리에 사용되므로 role 필수).
 *
 * <p>{@code tokenVersion} 은 RT 에만 존재 (AT 는 null). (role,userId) 별 단조 증가 정수로,
 * {@code RefreshTokenStore.revokeAll} 시 INCR 되어 이전 발급분 RT 를 한 번에 무효화한다.
 * 기존 SCAN→DEL 의 race 를 제거하기 위한 보강 (게이트 1).</p>
 */
public record JwtClaims(
        Long userId,
        String role,
        String jti,
        Long tokenVersion,
        Instant issuedAt,
        Instant expiresAt
) {
}
