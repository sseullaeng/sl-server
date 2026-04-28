package com.sseulang.global.security;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-for-hs256-blah-blah";
    private static final long AT_VALIDITY = 1800L;     // 30분
    private static final long RT_VALIDITY = 604800L;   // 7일
    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T03:00:00Z");

    private JwtProperties props;
    private Clock fixedClock;
    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        props = new JwtProperties(SECRET, AT_VALIDITY, RT_VALIDITY);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        provider = new JwtProvider(props, fixedClock);
    }

    @Test
    @DisplayName("AccessToken 발급_정상_userId/role/jti 포함되고 30분 뒤 만료")
    void issueAccessToken_정상_payload포함_30분만료() {
        String token = provider.issueAccessToken(42L, "USER");

        JwtClaims claims = provider.parse(token);
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo("USER");
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.issuedAt()).isEqualTo(FIXED_NOW);
        assertThat(claims.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(AT_VALIDITY));
    }

    @Test
    @DisplayName("RefreshToken 발급_정상_userId/role/jti 포함되고 7일 뒤 만료")
    void issueRefreshToken_정상_payload포함_7일만료() {
        String token = provider.issueRefreshToken(42L, "USER");

        JwtClaims claims = provider.parse(token);
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo("USER");
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(RT_VALIDITY));
    }

    @Test
    @DisplayName("AccessToken 두 번 발급_같은 userId여도 jti는 매번 다름")
    void issueAccessToken_매번다른jti() {
        String t1 = provider.issueAccessToken(1L, "USER");
        String t2 = provider.issueAccessToken(1L, "USER");

        assertThat(provider.parse(t1).jti()).isNotEqualTo(provider.parse(t2).jti());
    }

    @Test
    @DisplayName("parse_만료된 토큰_AUTH_TOKEN_EXPIRED 예외")
    void parse_만료된토큰_예외발생() {
        String token = provider.issueAccessToken(1L, "USER");

        Clock laterClock = Clock.fixed(FIXED_NOW.plusSeconds(AT_VALIDITY + 1), ZoneOffset.UTC);
        JwtProvider laterProvider = new JwtProvider(props, laterClock);

        assertThatThrownBy(() -> laterProvider.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("parse_변조된 서명_AUTH_TOKEN_INVALID 예외")
    void parse_변조된서명_예외발생() {
        String tampered = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.tamperedSignaturePart";

        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("parse_빈 문자열_AUTH_TOKEN_INVALID 예외")
    void parse_빈문자열_예외발생() {
        assertThatThrownBy(() -> provider.parse(""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("parse_null_AUTH_TOKEN_INVALID 예외")
    void parse_null_예외발생() {
        assertThatThrownBy(() -> provider.parse(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("parse_다른 secret으로 서명된 토큰_AUTH_TOKEN_INVALID 예외")
    void parse_다른secret_예외발생() {
        JwtProperties otherProps = new JwtProperties(
                "another-secret-must-be-at-least-32-bytes-long-for-hs256-okok",
                AT_VALIDITY, RT_VALIDITY);
        JwtProvider otherProvider = new JwtProvider(otherProps, fixedClock);
        String foreignToken = otherProvider.issueAccessToken(1L, "USER");

        assertThatThrownBy(() -> provider.parse(foreignToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("JwtProperties_secret이 32바이트 미만이면 생성 실패")
    void jwtProperties_짧은secret_예외() {
        assertThatThrownBy(() -> new JwtProperties("short", AT_VALIDITY, RT_VALIDITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
