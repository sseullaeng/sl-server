package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.AccessTokenBlacklist;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 단위 테스트용 in-memory fake. TTL 무시. */
class InMemoryFakeAccessTokenBlacklist implements AccessTokenBlacklist {

    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    @Override
    public void blacklist(String jti, Duration ttl) {
        blacklist.add(jti);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return blacklist.contains(jti);
    }

    int size() {
        return blacklist.size();
    }
}
