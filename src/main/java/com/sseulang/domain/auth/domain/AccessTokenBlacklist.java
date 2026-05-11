package com.sseulang.domain.auth.domain;

import java.time.Duration;

public interface AccessTokenBlacklist {

    

    void blacklist(String jti, Duration ttl);

    
    boolean isBlacklisted(String jti);
}
