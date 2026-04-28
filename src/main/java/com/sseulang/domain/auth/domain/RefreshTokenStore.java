package com.sseulang.domain.auth.domain;

import java.time.Duration;

/**
 * Refresh Token 저장소 인터페이스. 도메인 layer 순수 POJO — Spring/JPA/Redis 의존 X.
 * 구현은 {@code domain/auth/infrastructure/persistence}.
 *
 * Rotation 정책:
 *  - 토큰 발급 시 {@link #save} (TTL = 7일)
 *  - Rotation 시 {@link #consume} 으로 atomic 폐기 (반환 false 면 재사용 탐지)
 *  - 명시적 logout 시 {@link #revoke}
 *  - 재사용 탐지 시 {@link #revokeAll} 로 해당 사용자 전체 토큰 무효화
 */
public interface RefreshTokenStore {

    /** 발급 시 호출 — jti 를 지정 TTL 로 저장. */
    void save(Long userId, String jti, Duration ttl);

    /**
     * Rotation 의 핵심 — atomic 하게 jti 를 검증·폐기한다.
     * 저장돼 있던 jti 면 즉시 삭제하고 true. 없거나 이미 폐기됐으면 false.
     * isValid+revoke 두 단계를 race 없이 합친 형태.
     */
    boolean consume(Long userId, String jti);

    /** 명시적 logout 등 — 단일 jti 폐기. 없어도 무방. */
    void revoke(Long userId, String jti);

    /** 해당 사용자의 모든 Refresh Token 폐기 — 재사용 탐지·전체 로그아웃. */
    void revokeAll(Long userId);
}
