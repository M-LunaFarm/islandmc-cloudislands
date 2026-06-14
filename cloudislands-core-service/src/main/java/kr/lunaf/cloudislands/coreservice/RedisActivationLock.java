package kr.lunaf.cloudislands.coreservice;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.cache.RedisTtls;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class RedisActivationLock {
    private final URI redisUri;
    private final long ttlMillis;
    private final AtomicLong failures = new AtomicLong();

    public RedisActivationLock(URI redisUri, Duration ttl) {
        this.redisUri = redisUri;
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMillis(RedisTtls.LOCK_MAX_MILLIS) : ttl;
        this.ttlMillis = clampLockTtl(safeTtl.toMillis());
    }

    private long clampLockTtl(long requestedMillis) {
        return Math.max(RedisTtls.LOCK_MIN_MILLIS, Math.min(RedisTtls.LOCK_MAX_MILLIS, requestedMillis));
    }

    public Optional<Lease> acquire(UUID islandId, String owner) {
        String token = (owner == null || owner.isBlank() ? "core" : owner) + ":" + UUID.randomUUID();
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String response = redis.command("SET", RedisKeys.activationLock(islandId), token, "NX", "PX", Long.toString(ttlMillis));
            if ("OK".equalsIgnoreCase(response)) {
                return Optional.of(new Lease(islandId, token));
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
            String current = redis.command("GET", RedisKeys.activationLock(lease.islandId()));
            if (lease.token().equals(current)) {
                redis.command("DEL", RedisKeys.activationLock(lease.islandId()));
            }
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    public void releaseIfOwner(UUID islandId, String owner) {
        if (islandId == null) {
            return;
        }
        String prefix = (owner == null || owner.isBlank() ? "core" : owner) + ":";
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String current = redis.command("GET", RedisKeys.activationLock(islandId));
            if (current != null && current.startsWith(prefix)) {
                redis.command("DEL", RedisKeys.activationLock(islandId));
            }
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    public long failuresTotal() {
        return failures.get();
    }

    public record Lease(UUID islandId, String token) {}
}
