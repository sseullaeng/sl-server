package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.support.RedisIntegrationTestBase;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAccessTokenBlacklistIT extends RedisIntegrationTestBase {

    private RedisAccessTokenBlacklist blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new RedisAccessTokenBlacklist(redisTemplate());
    }

    @Test
    @DisplayName("blacklist 등록_isBlacklisted true")
    void blacklist_등록후_조회() {
        String jti = UUID.randomUUID().toString();

        blacklist.blacklist(jti, Duration.ofMinutes(30));

        assertThat(blacklist.isBlacklisted(jti)).isTrue();
    }

    @Test
    @DisplayName("blacklist 미등록 jti_isBlacklisted false")
    void blacklist_미등록_false() {
        assertThat(blacklist.isBlacklisted(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("TTL 만료_자동 정리")
    void blacklist_TTL_만료시_사라짐() {
        String jti = UUID.randomUUID().toString();
        blacklist.blacklist(jti, Duration.ofSeconds(1));

        assertThat(blacklist.isBlacklisted(jti)).isTrue();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> !blacklist.isBlacklisted(jti));
    }

    @Test
    @DisplayName("같은 jti 두 번 등록_TTL 갱신 (덮어쓰기)")
    void blacklist_재등록_TTL갱신() {
        String jti = UUID.randomUUID().toString();
        blacklist.blacklist(jti, Duration.ofSeconds(1));
        blacklist.blacklist(jti, Duration.ofMinutes(30));

        // 1초 지나도 살아있어야 함 (덮어쓴 TTL = 30분)
        Awaitility.await()
                .during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .until(() -> blacklist.isBlacklisted(jti));
    }
}
