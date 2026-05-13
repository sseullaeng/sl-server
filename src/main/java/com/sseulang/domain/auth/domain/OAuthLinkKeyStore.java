package com.sseulang.domain.auth.domain;

import com.sseulang.domain.user.domain.SocialProvider;

import java.time.Duration;
import java.util.Optional;

public interface OAuthLinkKeyStore {

    void save(String key, Long userId, SocialProvider provider, String providerId, Duration ttl);

    Optional<LinkKeyValue> consume(String key);

    record LinkKeyValue(Long userId, SocialProvider provider, String providerId) {
    }
}
