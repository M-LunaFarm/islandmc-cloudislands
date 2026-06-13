package kr.lunaf.cloudislands.coreservice.snapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Override
    public int prune(UUID islandId, int keepLatest) {
        Map<Long, IslandSnapshotRecord> islandSnapshots = snapshots.get(islandId);
        if (islandSnapshots == null || keepLatest < 0) {
            return 0;
        }
        List<Long> retained = islandSnapshots.keySet().stream()
            .sorted(Comparator.reverseOrder())
            .limit(keepLatest)
            .toList();
        int before = islandSnapshots.size();
        islandSnapshots.keySet().removeIf(snapshotNo -> !retained.contains(snapshotNo));
        return before - islandSnapshots.size();
    }

    @Override
    public int pruneRetaining(UUID islandId, Set<Long> retainedSnapshotNos) {
        Map<Long, IslandSnapshotRecord> islandSnapshots = snapshots.get(islandId);
        if (islandSnapshots == null || retainedSnapshotNos == null) {
            return 0;
        }
        int before = islandSnapshots.size();
        islandSnapshots.keySet().removeIf(snapshotNo -> !retainedSnapshotNos.contains(snapshotNo));
        return before - islandSnapshots.size();
    }
}
