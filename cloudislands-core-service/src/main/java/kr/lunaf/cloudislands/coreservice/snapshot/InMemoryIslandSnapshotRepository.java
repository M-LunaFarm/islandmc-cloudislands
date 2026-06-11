package kr.lunaf.cloudislands.coreservice.snapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

public final class InMemoryIslandSnapshotRepository implements IslandSnapshotRepository {
    private final Map<UUID, Map<Long, IslandSnapshotRecord>> snapshots = new ConcurrentHashMap<>();

    @Override
    public IslandSnapshotRecord record(UUID islandId, long snapshotNo, String storagePath, String reason, UUID createdBy, String checksum, long sizeBytes) {
        IslandSnapshotRecord record = new IslandSnapshotRecord(UUID.randomUUID(), islandId, snapshotNo, storagePath, reason, createdBy, checksum, sizeBytes, Instant.now());
        snapshots.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(snapshotNo, record);
        return record;
    }

    @Override
    public List<IslandSnapshotRecord> list(UUID islandId, int limit) {
        return snapshots.getOrDefault(islandId, Map.of()).values().stream()
            .sorted(Comparator.comparingLong(IslandSnapshotRecord::snapshotNo).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public Optional<IslandSnapshotRecord> find(UUID islandId, long snapshotNo) {
        return Optional.ofNullable(snapshots.getOrDefault(islandId, Map.of()).get(snapshotNo));
    }
}
