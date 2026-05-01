package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.RefreshTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Redis 백엔드.
 *
 * <h2>키 패턴</h2>
 * <ul>
 *   <li>{@code auth:rt:{ROLE}:{userId}:{jti}} → marker "1", TTL = RT 유효기간</li>
 *   <li>{@code auth:rtv:{ROLE}:{userId}} → 현재 tokenVersion (Long). 단조 증가, 부재 시 0 으로 간주.</li>
 * </ul>
 *
 * <h2>race-free 보장</h2>
 * <p>이전 SCAN→DEL 구현은 revokeAll 도중 동시 save 가 슬그머니 끼어들면 새 RT 가 살아남는 race
 * 가 있었다. 본 구현은 {@code consume} 을 Lua 로 (1) tokenVersion 검증, (2) jti DEL 한 번에
 * 묶어 race 를 제거한다. {@code revokeAll} 은 단순 INCR — 이후 발급된 RT 의 claim.tv 와
 * mismatch → consume 단계 즉시 거부. (게이트 1 보강)</p>
 *
 * <p>role 차원 격리는 동일 — user/admin 동일 숫자 id 공존 시 키 충돌 방지.</p>
 *
 * <p>TODO(Cluster): 추후 Redis Cluster 도입 시 multi-key Lua 가 같은 슬롯에 떨어지도록
 * hash tag {@code {ROLE:userId}} 적용 필요. 현재 standalone 사용으로 미적용.</p>
 */
@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:rt:";
    private static final String VERSION_KEY_PREFIX = "auth:rtv:";
    private static final String MARKER = "1";
    /**
     * revokeAll 직후 versionKey TTL — RT TTL(보통 7일) 보다 길게 잡아 사용자 재로그인 grace window 확보.
     * 30일 비활성 시 rtv 도 자연 회수 (follow-up #34).
     */
    private static final Duration VERSION_KEY_TTL_AFTER_REVOKE = Duration.ofDays(30);

    /**
     * Lua: (1) currentTv 조회 (없으면 "0"), (2) claimTv 와 비교, (3) 일치하면 DEL jti 시도.
     * <ul>
     *   <li>반환 -1 = 버전 mismatch (revokeAll 이후 발급된 RT 가 아닌 구버전)</li>
     *   <li>반환 0  = 버전은 맞지만 jti 가 이미 폐기됨 (재사용 탐지 시그널)</li>
     *   <li>반환 1  = 정상 consume</li>
     * </ul>
     */
    private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local cur = redis.call('GET', KEYS[1])
            if cur == false then cur = '0' end
            if cur ~= ARGV[1] then return -1 end
            return redis.call('DEL', KEYS[2])
            """,
            Long.class
    );

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long currentTokenVersion(String role, Long userId) {
        String v = redis.opsForValue().get(versionKey(role, userId));
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            // 손상된 값 — 보수적으로 0 처리 (다음 revokeAll 호출이 INCR 로 정상화).
            return 0L;
        }
    }

    @Override
    public void save(String role, Long userId, String jti, Duration ttl) {
        redis.opsForValue().set(jtiKey(role, userId, jti), MARKER, ttl);
        // versionKey TTL = RT TTL — 비활성 사용자 rtv 잔존 차단 (follow-up #34).
        // 기존 값 보존 + TTL 갱신 (없으면 "0" 으로 신규).
        String vKey = versionKey(role, userId);
        if (Boolean.TRUE.equals(redis.hasKey(vKey))) {
            redis.expire(vKey, ttl);
        } else {
            redis.opsForValue().setIfAbsent(vKey, "0", ttl);
        }
    }

    @Override
    public boolean consume(String role, Long userId, String jti, long claimTv) {
        Long result = redis.execute(
                CONSUME_SCRIPT,
                List.of(versionKey(role, userId), jtiKey(role, userId, jti)),
                Long.toString(claimTv)
        );
        // result == 1 → consume 성공. -1 / 0 / null 모두 false (호출자가 revokeAll + INVALID 처리).
        return result != null && result == 1L;
    }

    @Override
    public void revoke(String role, Long userId, String jti) {
        redis.delete(jtiKey(role, userId, jti));
    }

    @Override
    public void revokeAll(String role, Long userId) {
        // INCR 한 방. 진행 중 동시 save 가 있어도 이후 consume 의 tv 검증에서 mismatch 로 거부.
        // 폐기된 jti 키들은 TTL 만료까지 잔존하지만 의미상 무효 (consume 단계에서 막힘).
        String vKey = versionKey(role, userId);
        redis.opsForValue().increment(vKey);
        // INCR 가 새 키 만들면 TTL=-1 — 비활성 사용자 누적 차단 위해 명시 expire (follow-up #34).
        redis.expire(vKey, VERSION_KEY_TTL_AFTER_REVOKE);
    }

    private static String jtiKey(String role, Long userId, String jti) {
        return KEY_PREFIX + normalize(role) + ":" + userId + ":" + jti;
    }

    private static String versionKey(String role, Long userId) {
        return VERSION_KEY_PREFIX + normalize(role) + ":" + userId;
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role 은 필수입니다");
        }
        return role.toUpperCase(Locale.ROOT);
    }
}
