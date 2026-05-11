package com.sseulang.domain.auth.domain;

import java.time.Duration;

public interface RefreshTokenStore {

    

    long currentTokenVersion(String role, Long userId);

    
    void save(String role, Long userId, String jti, Duration ttl);

    

    boolean consume(String role, Long userId, String jti, long claimTv);

    
    void revoke(String role, Long userId, String jti);

    

    void revokeAll(String role, Long userId);
}
