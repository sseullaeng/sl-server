package com.sseulang.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis testcontainers 통합 테스트 공통 베이스. Spring 컨텍스트 없이 가볍게 동작.
 *
 * <p>컨테이너는 클래스 단위 1회 기동(static), {@link LettuceConnectionFactory} 는 매 테스트마다
 * 새로 만들고 destroy — 클래스 간 stale connection 방지.</p>
 */
@Testcontainers
public abstract class RedisIntegrationTestBase {

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void initRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void teardownRedis() {
        if (connectionFactory == null) {
            return;
        }
        try {
            connectionFactory.getConnection().serverCommands().flushDb();
        } catch (Exception ignored) {
            // 컨테이너가 죽은 경우 등 — 어차피 다음 테스트가 새로 만든다
        }
        try {
            connectionFactory.destroy();
        } catch (Exception ignored) {
        }
        connectionFactory = null;
        redisTemplate = null;
    }

    protected StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }
}
