package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.AccessTokenBlacklist;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtClaims;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Auth 세션 lifecycle.
 *
 * <ul>
 *   <li>{@code rotate}: oldRT 를 atomic 하게 consume → 통과 시 새 AT/RT 발급. 재사용 탐지 시 전체 폐기.</li>
 *   <li>{@code logout}: AT jti 를 블랙리스트에 등록 + RT 폐기. 만료/변조 토큰은 조용히 무시.</li>
 *   <li>{@code revoke}: RT 단일 폐기 — 내부용 호환 (logout 이 권장).</li>
 * </ul>
 */
@Service
@Transactional
public class RefreshTokenRotationService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public RefreshTokenRotationService(
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            AccessTokenBlacklist accessTokenBlacklist,
            Clock clock,
            JwtProperties jwtProperties
    ) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklist = accessTokenBlacklist;
        this.clock = clock;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
    }

    public TokenPair rotate(String oldRefreshToken) {
        JwtClaims claims = jwtProvider.parse(oldRefreshToken);  // AUTH_TOKEN_EXPIRED / INVALID
        Long userId = claims.userId();
        String role = claims.role();
        String oldJti = claims.jti();
        Long claimTv = claims.tokenVersion();
        if (role == null || role.isBlank() || claimTv == null) {
            // RT 에 role / tv 누락 — 토큰 변조 또는 구버전 토큰. 보수적으로 INVALID 응답.
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        // atomic Lua: (1) claimTv == currentTv 검증, (2) jti DEL.
        // false = (a) 버전 mismatch (revokeAll 이후) 또는 (b) 이미 사용된 jti → 탈취 의심.
        if (!refreshTokenStore.consume(role, userId, oldJti, claimTv)) {
            refreshTokenStore.revokeAll(role, userId);
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        String newAccessToken = jwtProvider.issueAccessToken(userId, role);
        long newTv = refreshTokenStore.currentTokenVersion(role, userId);
        String newRefreshToken = jwtProvider.issueRefreshToken(userId, role, newTv);
        String newJti = jwtProvider.parse(newRefreshToken).jti();
        refreshTokenStore.save(role, userId, newJti, refreshTokenTtl);

        return new TokenPair(newAccessToken, newRefreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        blacklistAccessToken(accessToken);
        revoke(refreshToken);
    }

    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            JwtClaims claims = jwtProvider.parse(refreshToken);
            String role = claims.role();
            if (role == null || role.isBlank()) {
                return;  // role 누락 토큰은 어차피 rotate 거부됨
            }
            refreshTokenStore.revoke(role, claims.userId(), claims.jti());
        } catch (BusinessException ignored) {
            // 만료/변조 토큰은 어차피 사용 불가 — 조용히 흘려보냄
        }
    }

    private void blacklistAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            JwtClaims claims = jwtProvider.parse(accessToken);
            long remainingSeconds = claims.expiresAt().getEpochSecond() - clock.instant().getEpochSecond();
            if (remainingSeconds > 0) {
                accessTokenBlacklist.blacklist(claims.jti(), Duration.ofSeconds(remainingSeconds));
            }
        } catch (BusinessException ignored) {
            // 만료/변조 AT 는 어차피 필터에서 거부됨
        }
    }
}
