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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OAuthLoginService {

    private static final String DEFAULT_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";

    private final Map<SocialProvider, OAuthProvider> providersByType;
    private final UserApplicationService userService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Duration refreshTokenTtl;

    

    private final Set<String> adminUserEmails;

    public OAuthLoginService(
            List<OAuthProvider> providers,
            UserApplicationService userService,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            JwtProperties jwtProperties,
            @Value("${app.admin.user-emails:}") String adminUserEmailsRaw
    ) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(OAuthProvider::supports, Function.identity()));
        this.userService = userService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
        this.adminUserEmails = parseAdminEmails(adminUserEmailsRaw);
        if (!adminUserEmails.isEmpty()) {
            log.warn("[admin-oauth] OAuth role=ADMIN 발급 대상 email {}건 활성 — app.admin.user-emails. "
                    + "운영 진입 전 의도된 대상인지 점검.", adminUserEmails.size());
        }
    }

    private static Set<String> parseAdminEmails(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
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

        
        if (!user.isAccessibleAt(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }

        
        userService.recordLogin(user.getId());

        
        
        
        String role = isAdminEmail(user.getEmail()) ? ADMIN_ROLE : DEFAULT_ROLE;
        if (ADMIN_ROLE.equals(role)) {
            log.warn("[admin-oauth][PROMOTE] OAuth user.id={} email={} → role=ADMIN JWT 발급",
                    user.getId(), user.getEmail());
        }

        String at = jwtProvider.issueAccessToken(user.getId(), role);
        
        long tv = refreshTokenStore.currentTokenVersion(role, user.getId());
        String rt = jwtProvider.issueRefreshToken(user.getId(), role, tv);
        String rtJti = jwtProvider.parse(rt).jti();
        refreshTokenStore.save(role, user.getId(), rtJti, refreshTokenTtl);

        return new TokenPair(at, rt);
    }

    private boolean isAdminEmail(String email) {
        if (email == null || adminUserEmails.isEmpty()) return false;
        return adminUserEmails.contains(email.toLowerCase(java.util.Locale.ROOT));
    }
}
