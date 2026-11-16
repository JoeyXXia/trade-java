package dev.commerceflow.trade.infrastructure;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisInfrastructureIT {
    private static final String PASSWORD = UUID.randomUUID().toString().replace("-", "");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(TestImages.locked("REDIS_IMAGE", "redis"))
        .withExposedPorts(6379)
        .withCommand("redis-server", "--requirepass", PASSWORD)
        .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
        .withStartupTimeout(Duration.ofMinutes(1));

    @Test
    void authenticatedCommandsWorkAndOwnKeysAreRemoved() {
        var uri = RedisURI.Builder.redis(REDIS.getHost(), REDIS.getMappedPort(6379))
            .withPassword(PASSWORD.toCharArray()).withTimeout(Duration.ofSeconds(5)).build();
        var client = RedisClient.create(uri);
        String key = "cf:test:" + UUID.randomUUID();
        try (var connection = client.connect()) {
            var commands = connection.sync();
            try {
                assertThat(commands.ping()).isEqualTo("PONG");
                assertThat(commands.exists(key)).isZero();
                commands.setex(key, 60, "day2");
                assertThat(commands.get(key)).isEqualTo("day2");
                assertThat(commands.ttl(key)).isBetween(1L, 60L);
            } finally {
                commands.del(key); // Never FLUSHDB/FLUSHALL.
            }
            assertThat(commands.exists(key)).isZero();
        } finally {
            client.shutdown();
        }
        var unauthenticated = RedisClient.create(RedisURI.Builder.redis(REDIS.getHost(), REDIS.getMappedPort(6379))
            .withTimeout(Duration.ofSeconds(5)).build());
        try {
            // RESP negotiation may reject authentication before connect() returns.
            assertThatThrownBy(() -> {
                try (var connection = unauthenticated.connect()) { connection.sync().ping(); }
            }).hasStackTraceContaining("NOAUTH");
        } finally {
            unauthenticated.shutdown();
        }
    }
}
