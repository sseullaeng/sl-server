package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소셜 로그인 흐름 — provider 검증 → user 조회/생성 → JWT 발급 + RT 저장.
 *
 * <p>본 PR 범위에선 role 은 항상 "USER" (일반 사용자). 관리자는 별도 admins 테이블로 관리.</p>
 */
/**
 * 클래스 단위 {@code @Transactional} 의도적으로 미적용 — 가이드 §3.10 "외부 API 호출은 트랜잭션
 * 밖에서". UserApplicationService 가 자체 트랜잭션을 가지며, RefreshTokenStore.save 는 Redis 라
 * JPA 트랜잭션 밖. AT 발급은 stateless.
 */
@Slf4j
@Service
public class OAuthLoginService {

    private static final String DEFAULT_ROLE = "USER";

    private final Map<SocialProvider, OAuthProvider> providersByType;
    private final UserApplicationService userService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Duration refreshTokenTtl;

    public OAuthLoginService(
            List<OAuthProvider> providers,
            UserApplicationService userService,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            JwtProperties jwtProperties
    ) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(OAuthProvider::supports, Function.identity()));
        this.userService = userService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
    }

    public TokenPair loginWithCode(SocialProvider provider, String code, String redirectUri) {
        OAuthProvider impl = providersByType.get(provider);
        if (impl == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        OAuthUserInfo info;
        try {
            info = impl.exchangeCodeAndFetch(code, redirectUri);
        } catch (ExternalApiException e) {
            // 외부 인프라 예외 → 사용자 응답은 AUTH_OAUTH_FAILED 통일.
            // 원인은 cause 에 보존 + 운영용 로그.
            log.warn("OAuth provider {} 호출 실패: {}", provider, e.getExternalServiceName(), e);
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        User user = userService.findOrCreateBySocial(
                info.provider(),
                info.providerId(),
                info.email(),
                info.nickname(),
                info.profileImage()
        );

        // SUSPENDED (시한부 정지) 도 USER_BLOCKED 통합 — Codex round 9 hotfix.
        if (!user.isAccessibleAt(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }

        // 휴면 판정 기준 — 응답 status 가 ACTIVE 로 자동 복귀.
        userService.recordLogin(user.getId());

        String at = jwtProvider.issueAccessToken(user.getId(), DEFAULT_ROLE);
        // tv: store 의 현재 버전을 RT claim 에 박는다. 이후 revokeAll → INCR 시 본 RT 는 mismatch 로 거부됨.
        long tv = refreshTokenStore.currentTokenVersion(DEFAULT_ROLE, user.getId());
        String rt = jwtProvider.issueRefreshToken(user.getId(), DEFAULT_ROLE, tv);
        String rtJti = jwtProvider.parse(rt).jti();
        refreshTokenStore.save(DEFAULT_ROLE, user.getId(), rtJti, refreshTokenTtl);

        return new TokenPair(at, rt);
    }
}
