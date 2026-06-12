package kr.lunaf.cloudislands.coreservice;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class RedisPlayerCreationLock {
    private final URI redisUri;
    private final long ttlMillis;
    private final AtomicLong failures = new AtomicLong();

    public RedisPlayerCreationLock(URI redisUri, Duration ttl) {
        this.redisUri = redisUri;
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        this.ttlMillis = Math.max(1_000L, safeTtl.toMillis());
    }

    public Optional<Lease> acquire(UUID playerUuid) {
        String token = "create:" + UUID.randomUUID();
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String response = redis.command("SET", RedisKeys.playerCreateLock(playerUuid), token, "NX", "PX", Long.toString(ttlMillis));
            if ("OK".equalsIgnoreCase(response)) {
                return Optional.of(new Lease(playerUuid, token));
            }
            return Optional.empty();
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    public void release(Lease lease) {
        if (lease == null) {
            return;
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String current = redis.command("GET", RedisKeys.playerCreateLock(lease.playerUuid()));
            if (lease.token().equals(current)) {
                redis.command("DEL", RedisKeys.playerCreateLock(lease.playerUuid()));
            }
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    public long failuresTotal() {
        return failures.get();
    }

    public record Lease(UUID playerUuid, String token) {}
}
