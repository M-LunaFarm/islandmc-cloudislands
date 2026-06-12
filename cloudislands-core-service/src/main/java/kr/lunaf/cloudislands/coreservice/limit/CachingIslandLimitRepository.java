package kr.lunaf.cloudislands.coreservice.limit;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandLimitRepository implements IslandLimitRepository {
    private final IslandLimitRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandLimitRepository(IslandLimitRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public List<IslandLimitSnapshot> list(UUID islandId) {
        Optional<List<IslandLimitSnapshot>> cached = cached(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cache(islandId, delegate.list(islandId));
    }

    @Override
    public IslandLimitSnapshot set(UUID islandId, String limitKey, long value, UUID updatedBy) {
        IslandLimitSnapshot snapshot = delegate.set(islandId, limitKey, value, updatedBy);
        cache(islandId, delegate.list(islandId));
        return snapshot;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private List<IslandLimitSnapshot> cache(UUID islandId, List<IslandLimitSnapshot> limits) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandLimits(islandId), encode(limits));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return limits;
    }

    private Optional<List<IslandLimitSnapshot>> cached(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandLimits(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(List<IslandLimitSnapshot> limits) {
        StringBuilder out = new StringBuilder();
        for (IslandLimitSnapshot limit : limits) {
            out.append(limit.islandId()).append('|')
                .append(limit.limitKey()).append('|')
                .append(limit.value()).append('|')
                .append(limit.updatedBy()).append('|')
                .append(limit.updatedAt())
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandLimitSnapshot> parse(String value) {
        List<IslandLimitSnapshot> limits = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) {
                continue;
            }
            limits.add(new IslandLimitSnapshot(
                UUID.fromString(parts[0]),
                parts[1],
                Long.parseLong(parts[2]),
                UUID.fromString(parts[3]),
                instant(parts[4])
            ));
        }
        return List.copyOf(limits);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
