package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.OAuthLinkKeyStore;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OAuthAccountLinkService {

    private static final Duration LINK_KEY_TTL = Duration.ofMinutes(5);

    private final Map<SocialProvider, OAuthProvider> providersByType;
    private final UserApplicationService userService;
    private final OAuthLinkKeyStore linkKeyStore;

    public OAuthAccountLinkService(
            List<OAuthProvider> providers,
            UserApplicationService userService,
            OAuthLinkKeyStore linkKeyStore
    ) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(OAuthProvider::supports, Function.identity()));
        this.userService = userService;
        this.linkKeyStore = linkKeyStore;
    }

    public LinkPreviewResult preview(Long currentUserId, SocialProvider provider, String code, String redirectUri) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        OAuthProvider impl = providersByType.get(provider);
        if (impl == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        User current = userService.getById(currentUserId);
        if (current.getSocialProvider() != null && current.getSocialProvider() != SocialProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_NOT_LOCAL);
        }

        OAuthUserInfo info;
        try {
            info = impl.exchangeCodeAndFetch(code, redirectUri);
        } catch (ExternalApiException e) {
            log.warn("[oauth-link] provider {} 호출 실패", provider, e);
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
        String providerEmail = info.email() == null ? null : info.email().value();
        if (providerEmail == null || !providerEmail.equalsIgnoreCase(current.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_EMAIL_MISMATCH);
        }

        String key = UUID.randomUUID().toString();
        linkKeyStore.save(key, currentUserId, info.provider(), info.providerId(), LINK_KEY_TTL);
        return new LinkPreviewResult(key, info.provider(), providerEmail, (int) LINK_KEY_TTL.toSeconds());
    }

    public User confirm(Long currentUserId, String linkKey) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        OAuthLinkKeyStore.LinkKeyValue v = linkKeyStore.consume(linkKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_OAUTH_LINK_KEY_INVALID));
        if (!v.userId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_KEY_INVALID);
        }
        User current = userService.getById(currentUserId);
        return userService.addSocialLink(currentUserId, v.provider(), v.providerId(), current.getEmail());
    }

    public record LinkPreviewResult(String linkKey, SocialProvider provider, String providerEmail, int expiresInSeconds) {
    }
}
