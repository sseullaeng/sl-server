package com.sseulang.domain.auth.domain;

import java.time.Duration;

/**
 * Refresh Token 저장소 인터페이스. 도메인 layer 순수 POJO — Spring/JPA/Redis 의존 X.
 * 구현은 {@code domain/auth/infrastructure/persistence}.
 *
 * <p>Key 차원: {@code (role, userId, jti)} — 일반 사용자(role=USER) 와 관리자(role=ADMIN) 가
 * 동일 숫자 id 를 가질 수 있으므로 role 도 포함해야 세션 격리가 보장된다.</p>
 *
 * <h2>Token Version (게이트 1 race 보강)</h2>
 * 각 (role, userId) 마다 단조 증가 정수 {@code tokenVersion} 을 둔다.
 * <ul>
 *   <li>RT 발급 직전 {@link #currentTokenVersion} 으로 현재 값 조회 → JWT claim {@code tv} 로 박는다.</li>
 *   <li>{@link #revokeAll} 은 INCR 한 번으로 종료 (SCAN 없음). 이후 발급된 RT 의 claim.tv 와
 *       mismatch → consume 단계에서 즉시 거부.</li>
 *   <li>{@link #consume} 은 (claimTv == currentTv) 검증 + jti 폐기를 원자 실행 (Redis Lua).</li>
 * </ul>
 *
 * <p>이전 SCAN→DEL 구현은 revokeAll 도중 동시 save 된 RT 가 살아남는 race 가 있었다.
 * tokenVersion 은 키 검사 자체를 무력화하므로 race-free.</p>
 *
 * <h2>Rotation 정책</h2>
 * <ul>
 *   <li>발급: {@link #currentTokenVersion} → JWT 발급 → {@link #save}</li>
 *   <li>Rotation: {@link #consume} 으로 atomic 검증·폐기 (false 면 재사용 탐지)</li>
 *   <li>logout: {@link #revoke}</li>
 *   <li>재사용 탐지·강제 로그아웃: {@link #revokeAll} (INCR)</li>
 * </ul>
 */
public interface RefreshTokenStore {

    /**
     * 현재 (role, userId) 의 tokenVersion 조회. 한 번도 revokeAll 호출되지 않았으면 0 반환.
     * RT 발급 직전 호출하여 JWT claim {@code tv} 로 박는다.
     */
    long currentTokenVersion(String role, Long userId);

    /** 발급 시 호출 — (role, userId, jti) 를 지정 TTL 로 저장. tv 는 JWT claim 에만 보관. */
    void save(String role, Long userId, String jti, Duration ttl);

    /**
     * Rotation 의 핵심 — atomic 하게 (1) claimTv==currentTv 검증, (2) jti 폐기.
     * 둘 다 통과하면 true. 아니면 false (= 버전 mismatch 또는 이미 폐기된 jti).
     * isValid+revoke 두 단계를 Redis Lua 로 race 없이 합친 형태.
     */
    boolean consume(String role, Long userId, String jti, long claimTv);

    /** 명시적 logout 등 — 단일 jti 폐기. 없어도 무방. */
    void revoke(String role, Long userId, String jti);

    /**
     * 해당 (role, userId) 의 모든 Refresh Token 폐기 — INCR tokenVersion 한 번.
     * 진행 중 동시 save 가 있어도 이후 consume 에서 모두 mismatch 로 거부됨.
     */
    void revokeAll(String role, Long userId);
}
