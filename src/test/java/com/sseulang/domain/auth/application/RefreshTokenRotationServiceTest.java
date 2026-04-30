package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtClaims;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenRotationServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-for-hs256-blah-blah";
    private static final long AT_VALIDITY = 1800L;
    private static final long RT_VALIDITY = 604800L;
    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T03:00:00Z");
    private static final Long USER_ID = 42L;

    private JwtProperties props;
    private JwtProvider jwt;
    private InMemoryFakeRefreshTokenStore store;
    private InMemoryFakeAccessTokenBlacklist blacklist;
    private Clock fixedClock;
    private RefreshTokenRotationService service;

    @BeforeEach
    void setUp() {
        props = new JwtProperties(SECRET, AT_VALIDITY, RT_VALIDITY);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        jwt = new JwtProvider(props, fixedClock);
        store = new InMemoryFakeRefreshTokenStore();
        blacklist = new InMemoryFakeAccessTokenBlacklist();
        service = new RefreshTokenRotationService(jwt, store, blacklist, fixedClock, props);
    }

    /** login 시점 시뮬레이션: tv 조회 → 토큰 발급 + store 등록. */
    private String issueAndRegister(Long userId, String role) {
        long tv = store.currentTokenVersion(role, userId);
        String rt = jwt.issueRefreshToken(userId, role, tv);
        store.save(role, userId, jwt.parse(rt).jti(), Duration.ofSeconds(RT_VALIDITY));
        return rt;
    }

    @Test
    @DisplayName("rotate_정상_새 AT/RT 발급 + old jti revoke + new jti save")
    void rotate_정상() {
        String oldRt = issueAndRegister(USER_ID, "USER");
        String oldJti = jwt.parse(oldRt).jti();

        TokenPair pair = service.rotate(oldRt);

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken())
                .isNotBlank()
                .isNotEqualTo(oldRt);

        // old jti 무효화
        assertThat(store.contains("USER", USER_ID, oldJti)).isFalse();
        // 새 jti 등록
        JwtClaims newClaims = jwt.parse(pair.refreshToken());
        assertThat(store.contains("USER", USER_ID, newClaims.jti())).isTrue();
        assertThat(newClaims.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("rotate_새 AT 의 role 이 RT role 과 동일")
    void rotate_새AT의_role_유지() {
        String rt = issueAndRegister(USER_ID, "ADMIN");

        TokenPair pair = service.rotate(rt);

        assertThat(jwt.parse(pair.accessToken()).role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("rotate_재사용 탐지_AUTH_REFRESH_TOKEN_INVALID + 해당 사용자 RT 전체 무효화")
    void rotate_재사용탐지() {
        // 정상 발급 + store 등록 후 한 번 사용해서 폐기된 상태 가정
        String reusedRt = issueAndRegister(USER_ID, "USER");
        TokenPair first = service.rotate(reusedRt);  // 정상 회전 — old 폐기
        // 다른 디바이스에서도 토큰 등록되어 있다고 가정 (동일 사용자)
        String otherDeviceRt = issueAndRegister(USER_ID, "USER");
        // first 후 새 RT 도 store 에 있음
        assertThat(store.contains("USER", USER_ID, jwt.parse(first.refreshToken()).jti())).isTrue();
        assertThat(store.contains("USER", USER_ID, jwt.parse(otherDeviceRt).jti())).isTrue();

        // 이미 폐기된 reusedRt 를 다시 들고 옴 → 탈취 의심 → revokeAll (INCR tv)
        assertThatThrownBy(() -> service.rotate(reusedRt))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);

        // tokenVersion 시맨틱: revokeAll 은 INCR 한 방. jti 키는 TTL 만료까지 잔존하지만 의미상 무효.
        // 무효화 검증은 "rotate 시도 시 거부" 로 — 해당 사용자의 어떤 RT 든 더이상 rotate 불가.
        assertThatThrownBy(() -> service.rotate(first.refreshToken()))
                .as("first 의 새 RT 도 무효화되어야 함")
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        assertThatThrownBy(() -> service.rotate(otherDeviceRt))
                .as("다른 디바이스 RT 도 무효화되어야 함")
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("rotate_만료된 RT_AUTH_TOKEN_EXPIRED")
    void rotate_만료RT() {
        String rt = issueAndRegister(USER_ID, "USER");

        Clock laterClock = Clock.fixed(FIXED_NOW.plusSeconds(RT_VALIDITY + 1), ZoneOffset.UTC);
        JwtProvider laterJwt = new JwtProvider(props, laterClock);
        RefreshTokenRotationService laterService = new RefreshTokenRotationService(laterJwt, store, blacklist, laterClock, props);

        assertThatThrownBy(() -> laterService.rotate(rt))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("rotate_변조된 RT_AUTH_TOKEN_INVALID")
    void rotate_변조RT() {
        String tampered = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiJ9.fake";

        assertThatThrownBy(() -> service.rotate(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("revoke_정상_RT 폐기됨")
    void revoke_정상() {
        String rt = issueAndRegister(USER_ID, "USER");
        String jti = jwt.parse(rt).jti();
        assertThat(store.contains("USER", USER_ID, jti)).isTrue();

        service.revoke(rt);

        assertThat(store.contains("USER", USER_ID, jti)).isFalse();
    }

    @Test
    @DisplayName("revoke_변조 토큰_조용히 흘려보냄 (예외 X)")
    void revoke_변조토큰_무시() {
        assertThatCode(() -> service.revoke("totally.invalid.token"))
                .doesNotThrowAnyException();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("revoke_만료 토큰_조용히 흘려보냄 (예외 X)")
    void revoke_만료토큰_무시() {
        String rt = issueAndRegister(USER_ID, "USER");

        Clock laterClock = Clock.fixed(FIXED_NOW.plusSeconds(RT_VALIDITY + 1), ZoneOffset.UTC);
        JwtProvider laterJwt = new JwtProvider(props, laterClock);
        RefreshTokenRotationService laterService = new RefreshTokenRotationService(laterJwt, store, blacklist, laterClock, props);

        assertThatCode(() -> laterService.revoke(rt))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logout_정상_AT 블랙리스트 등록 + RT 폐기")
    void logout_정상() {
        String rt = issueAndRegister(USER_ID, "USER");
        String rtJti = jwt.parse(rt).jti();
        String at = jwt.issueAccessToken(USER_ID, "USER");
        String atJti = jwt.parse(at).jti();

        service.logout(at, rt);

        assertThat(blacklist.isBlacklisted(atJti)).isTrue();
        assertThat(store.contains("USER", USER_ID, rtJti)).isFalse();
    }

    @Test
    @DisplayName("logout_AT 만료_블랙리스트 등록 X (어차피 필터에서 거부됨)")
    void logout_만료AT_블랙리스트X() {
        String rt = issueAndRegister(USER_ID, "USER");
        String at = jwt.issueAccessToken(USER_ID, "USER");
        String atJti = jwt.parse(at).jti();

        Clock laterClock = Clock.fixed(FIXED_NOW.plusSeconds(AT_VALIDITY + 1), ZoneOffset.UTC);
        JwtProvider laterJwt = new JwtProvider(props, laterClock);
        RefreshTokenRotationService laterService = new RefreshTokenRotationService(laterJwt, store, blacklist, laterClock, props);

        laterService.logout(at, rt);

        assertThat(blacklist.isBlacklisted(atJti)).isFalse();
        assertThat(blacklist.size()).isZero();
    }

    @Test
    @DisplayName("logout_AT 변조_블랙리스트 등록 X (예외 X)")
    void logout_변조AT_블랙리스트X() {
        String rt = issueAndRegister(USER_ID, "USER");
        String rtJti = jwt.parse(rt).jti();

        assertThatCode(() -> service.logout("totally.invalid.token", rt))
                .doesNotThrowAnyException();

        assertThat(blacklist.size()).isZero();
        // RT 는 정상이라 폐기됨
        assertThat(store.contains("USER", USER_ID, rtJti)).isFalse();
    }

    @Test
    @DisplayName("logout_AT/RT 모두 null_no-op (예외 X)")
    void logout_둘다null_무시() {
        assertThatCode(() -> service.logout(null, null))
                .doesNotThrowAnyException();
        assertThat(blacklist.size()).isZero();
        assertThat(store.size()).isZero();
    }
}
