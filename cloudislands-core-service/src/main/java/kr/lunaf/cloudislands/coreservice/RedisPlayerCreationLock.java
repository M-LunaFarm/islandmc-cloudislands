package kr.lunaf.cloudislands.coreservice;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.cache.RedisTtls;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class RedisPlayerCreationLock {
    private final URI redisUri;
    private final long ttlMillis;
    private final boolean localFallbackEnabled;
    private final AtomicLong failures = new AtomicLong();
    private final Map<UUID, LocalLock> localLocks = new ConcurrentHashMap<>();
    private static final String RELEASE_IF_TOKEN_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0";

    public RedisPlayerCreationLock(URI redisUri, Duration ttl) {
        this(redisUri, ttl, false);
    }

    public RedisPlayerCreationLock(URI redisUri, Duration ttl, boolean localFallbackEnabled) {
        this.redisUri = redisUri;
        this.localFallbackEnabled = localFallbackEnabled;
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        this.ttlMillis = clampLockTtl(safeTtl.toMillis());
    }

    private long clampLockTtl(long requestedMillis) {
        return Math.max(RedisTtls.LOCK_MIN_MILLIS, Math.min(RedisTtls.LOCK_MAX_MILLIS, requestedMillis));
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
            if (!localFallbackEnabled) {
                return Optional.empty();
            }
            return acquireLocal(playerUuid, token);
        }
    }

    public void release(Lease lease) {
        if (lease == null) {
            return;
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("EVAL", RELEASE_IF_TOKEN_SCRIPT, "1", RedisKeys.playerCreateLock(lease.playerUuid()), lease.token());
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        } finally {
            if (localFallbackEnabled) {
                releaseLocal(lease);
            }
        }
    }

    public long failuresTotal() {
        return failures.get();
    }

    public record Lease(UUID playerUuid, String token) {}

    private Optional<Lease> acquireLocal(UUID playerUuid, String token) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlMillis;
        LocalLock next = new LocalLock(token, expiresAt);
        LocalLock lock = localLocks.compute(playerUuid, (_key, current) -> current == null || current.expiresAt() <= now ? next : current);
        return next.equals(lock) ? Optional.of(new Lease(playerUuid, token)) : Optional.empty();
    }

    private void releaseLocal(Lease lease) {
        localLocks.computeIfPresent(lease.playerUuid(), (_key, current) -> lease.token().equals(current.token()) ? null : current);
    }

    private record LocalLock(String token, long expiresAt) {}
}
