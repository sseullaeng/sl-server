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

@Service
@Transactional
public class RefreshTokenRotationService {

    private static final String USER_ROLE = "USER";

    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final com.sseulang.domain.user.domain.UserRepository userRepository;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public RefreshTokenRotationService(
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            AccessTokenBlacklist accessTokenBlacklist,
            com.sseulang.domain.user.domain.UserRepository userRepository,
            Clock clock,
            JwtProperties jwtProperties
    ) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklist = accessTokenBlacklist;
        this.userRepository = userRepository;
        this.clock = clock;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
    }

    public TokenPair rotate(String oldRefreshToken) {
        JwtClaims claims = jwtProvider.parse(oldRefreshToken);  
        Long userId = claims.userId();
        String role = claims.role();
        String oldJti = claims.jti();
        Long claimTv = claims.tokenVersion();
        if (role == null || role.isBlank() || claimTv == null) {
            
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        
        
        if (!refreshTokenStore.consume(role, userId, oldJti, claimTv)) {
            refreshTokenStore.revokeAll(role, userId);
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        
        
        
        if (USER_ROLE.equals(role)) {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isPresent() && !userOpt.get().isAccessibleAt(java.time.LocalDateTime.now(clock))) {
                refreshTokenStore.revokeAll(role, userId);
                throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
            }
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
                return;  
            }
            refreshTokenStore.revoke(role, claims.userId(), claims.jti());
        } catch (BusinessException ignored) {
            
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
            
        }
    }
}
