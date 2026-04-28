package com.sseulang.domain.auth.domain;

import java.time.Duration;

/**
 * Access Token jti 블랙리스트 — 명시적 logout 시 즉시 폐기 흐름.
 *
 * AT 는 만료가 짧지만(30분) logout 직후의 위험 윈도우를 막기 위해 jti 단위로 폐기 가능해야 한다.
 * (가이드 §4.1 의 {@code AUTH_TOKEN_REVOKED} 사용 의도)
 *
 * 디바이스 단위 — 한 디바이스에서 logout 해도 다른 디바이스의 AT 는 유효.
 */
public interface AccessTokenBlacklist {

    /**
     * jti 를 블랙리스트에 등록. TTL 은 보통 AT 의 잔여 만료시간으로 잡으면
     * 자연 만료와 함께 자동 정리된다.
     */
    void blacklist(String jti, Duration ttl);

    /** 블랙리스트 hit 인지 확인. */
    boolean isBlacklisted(String jti);
}
