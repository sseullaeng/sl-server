package com.sseulang.global.security;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final JwtProperties props;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtProvider(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(key)
                .build();
    }

    public String issueAccessToken(Long userId, String role) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(props.accessTokenValiditySeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * RT 발급. {@code tokenVersion} 은 호출 직전에 {@code RefreshTokenStore.currentTokenVersion}
     * 으로 조회한 값. (role,userId) 의 revokeAll 시 store 측 tv 가 INCR 되어, 이 RT 의
     * claim.tv 와 mismatch → 다음 rotate 에서 거부된다.
     */
    public String issueRefreshToken(Long userId, String role, long tokenVersion) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(props.refreshTokenValiditySeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public JwtClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            Claims c = jws.getPayload();
            return new JwtClaims(
                    Long.valueOf(c.getSubject()),
                    c.get(CLAIM_ROLE, String.class),
                    c.getId(),
                    extractTokenVersion(c),
                    c.getIssuedAt().toInstant(),
                    c.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    /**
     * tv claim 추출 — RT 에는 항상 존재, AT 에는 없음 (null). 정수형이면 Number 로,
     * 누락이면 null 반환. 호출자(RotationService) 가 null 인 RT 를 INVALID 로 처리한다.
     */
    private static Long extractTokenVersion(Claims c) {
        Object v = c.get(CLAIM_TOKEN_VERSION);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
