package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.RefreshTokenStore;

import java.time.Duration;

/**
 * 단위 테스트용 no-op {@link RefreshTokenStore}.
 *
 * <p>RT 의미가 검증 대상이 아닌 service 단위 테스트가 사용 (UserApplicationService.adminSuspend 가
 * revokeAll 호출하는 케이스 등). RT race / rotation 자체 검증은 {@link InMemoryFakeRefreshTokenStore}
 * 또는 통합 IT 가 담당.</p>
 */
public class NoOpRefreshTokenStore implements RefreshTokenStore {
    @Override public long currentTokenVersion(String role, Long userId) { return 0; }
    @Override public void save(String role, Long userId, String jti, Duration ttl) { }
    @Override public boolean consume(String role, Long userId, String jti, long claimTv) { return true; }
    @Override public void revoke(String role, Long userId, String jti) { }
    @Override public void revokeAll(String role, Long userId) { }
}
