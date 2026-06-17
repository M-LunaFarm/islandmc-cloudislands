package kr.lunaf.cloudislands.coreservice.snapshot;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.failure.RedisOutagePolicy;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class CachingIslandSnapshotRepository implements IslandSnapshotRepository {
    private static final int CACHE_LIMIT = 100;

    private final IslandSnapshotRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandSnapshotRepository(IslandSnapshotRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public IslandSnapshotRecord record(UUID islandId, long snapshotNo, String storagePath, String reason, UUID createdBy, String checksum, long sizeBytes) {
        IslandSnapshotRecord record = delegate.record(islandId, snapshotNo, storagePath, reason, createdBy, checksum, sizeBytes);
        refresh(islandId);
        return record;
    }

    @Override
    public List<IslandSnapshotRecord> list(UUID islandId, int limit) {
        Optional<List<IslandSnapshotRecord>> cached = cached(islandId);
        if (cached.isPresent()) {
            return cached.get().stream().limit(Math.max(1, limit)).toList();
        }
        return refresh(islandId).stream().limit(Math.max(1, limit)).toList();
    }

    @Override
    public Optional<IslandSnapshotRecord> find(UUID islandId, long snapshotNo) {
        Optional<List<IslandSnapshotRecord>> cached = cached(islandId);
        if (cached.isPresent()) {
            Optional<IslandSnapshotRecord> record = cached.get().stream()
                .filter(snapshot -> snapshot.snapshotNo() == snapshotNo)
                .findFirst();
            if (record.isPresent()) {
                return record;
            }
        }
        Optional<IslandSnapshotRecord> record = delegate.find(islandId, snapshotNo);
        if (record.isPresent()) {
            refresh(islandId);
        }
        return record;
    }

    @Override
    public int prune(UUID islandId, int keepLatest) {
        int pruned = delegate.prune(islandId, keepLatest);
        refresh(islandId);
        return pruned;
    }

    @Override
    public int prune(UUID islandId, SnapshotRetentionPolicy policy) {
        int pruned = delegate.prune(islandId, policy);
        refresh(islandId);
        return pruned;
    }

    @Override
    public int pruneRetaining(UUID islandId, Set<Long> retainedSnapshotNos) {
        int pruned = delegate.pruneRetaining(islandId, retainedSnapshotNos);
        refresh(islandId);
        return pruned;
    }

    public long failuresTotal() {
        return failures.get();
    }

    public String degradedModePolicy() {
        return RedisOutagePolicy.DB_DIRECT_READ_POLICY;
    }

    private List<IslandSnapshotRecord> refresh(UUID islandId) {
        return cache(islandId, delegate.list(islandId, CACHE_LIMIT));
    }

    private List<IslandSnapshotRecord> cache(UUID islandId, List<IslandSnapshotRecord> snapshots) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandSnapshots(islandId), encode(snapshots));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return snapshots;
    }

    private Optional<List<IslandSnapshotRecord>> cached(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandSnapshots(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(List<IslandSnapshotRecord> snapshots) {
        StringBuilder out = new StringBuilder();
        for (IslandSnapshotRecord snapshot : snapshots) {
            out.append(snapshot.snapshotId()).append('|')
                .append(snapshot.islandId()).append('|')
                .append(snapshot.snapshotNo()).append('|')
                .append(encodeText(snapshot.storagePath())).append('|')
                .append(encodeText(snapshot.reason())).append('|')
                .append(snapshot.createdBy() == null ? "" : snapshot.createdBy()).append('|')
                .append(encodeText(snapshot.checksum())).append('|')
                .append(snapshot.sizeBytes()).append('|')
                .append(snapshot.createdAt())
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandSnapshotRecord> parse(String value) {
        List<IslandSnapshotRecord> snapshots = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 9) {
                continue;
            }
            try {
                snapshots.add(new IslandSnapshotRecord(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    Long.parseLong(parts[2]),
                    decodeText(parts[3]),
                    decodeText(parts[4]),
                    parts[5].isBlank() ? null : UUID.fromString(parts[5]),
                    decodeText(parts[6]),
                    Long.parseLong(parts[7]),
                    instant(parts[8])
                ));
            } catch (RuntimeException ignored) {
                // Skip a corrupt Redis cache row instead of discarding the whole cached snapshot list.
            }
        }
        return List.copyOf(snapshots);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encodedBase64) {
        return new String(Base64.getUrlDecoder().decode(encodedBase64), StandardCharsets.UTF_8);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
