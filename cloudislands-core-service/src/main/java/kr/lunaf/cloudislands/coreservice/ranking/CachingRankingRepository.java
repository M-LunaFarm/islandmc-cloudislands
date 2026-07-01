package kr.lunaf.cloudislands.coreservice.ranking;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingRankingRepository implements RankingRepository {
    private final RankingRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingRankingRepository(RankingRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public void markDirty(UUID islandId) {
        delegate.markDirty(islandId);
        bumpVersion();
    }

    @Override
    public List<UUID> drainDirty(int limit) {
        return delegate.drainDirty(limit);
    }

    @Override
    public long dirtyCount() {
        return delegate.dirtyCount();
    }

    @Override
    public void save(IslandRankSnapshot snapshot) {
        delegate.save(snapshot);
        bumpVersion();
    }

    @Override
    public List<IslandRankSnapshot> topByLevel(int limit) {
        return cachedTop("level", limit, () -> delegate.topByLevel(Math.max(1, limit)));
    }

    @Override
    public List<IslandRankSnapshot> topByWorth(int limit) {
        return cachedTop("worth", limit, () -> delegate.topByWorth(Math.max(1, limit)));
    }

    public long failuresTotal() {
        return failures.get();
    }

    private List<IslandRankSnapshot> cachedTop(String metric, int limit, Supplier<List<IslandRankSnapshot>> loader) {
        int safeLimit = Math.max(1, limit);
        Long version = version();
        if (version != null) {
            Optional<List<IslandRankSnapshot>> cached = readTop(metric, safeLimit, version);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        List<IslandRankSnapshot> snapshots = loader.get();
        if (version != null) {
            writeTop(metric, safeLimit, version, snapshots);
        }
        return snapshots;
    }

    private Long version() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.rankingVersion());
            if (value == null || value.isBlank()) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return null;
        }
    }

    private void bumpVersion() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("INCR", RedisKeys.rankingVersion());
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandRankSnapshot>> readTop(String metric, int limit, long version) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.rankingTop(metric, limit, version));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void writeTop(String metric, int limit, long version, List<IslandRankSnapshot> snapshots) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.rankingTop(metric, limit, version), encode(snapshots));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private static String encode(List<IslandRankSnapshot> snapshots) {
        StringBuilder out = new StringBuilder();
        for (IslandRankSnapshot snapshot : snapshots) {
            out.append(snapshot.islandId()).append('|')
                .append(snapshot.level()).append('|')
                .append(snapshot.worth()).append('|')
                .append(snapshot.memberCount()).append('|')
                .append(snapshot.updatedAt())
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandRankSnapshot> parse(String value) {
        List<IslandRankSnapshot> snapshots = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) {
                continue;
            }
            try {
                snapshots.add(new IslandRankSnapshot(
                    UUID.fromString(parts[0]),
                    Long.parseLong(parts[1]),
                    new BigDecimal(parts[2]),
                    Integer.parseInt(parts[3]),
                    Instant.parse(parts[4])
                ));
            } catch (RuntimeException ignored) {
            }
        }
        return List.copyOf(snapshots);
    }
}
