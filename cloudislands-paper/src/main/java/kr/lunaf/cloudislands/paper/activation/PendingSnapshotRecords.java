package kr.lunaf.cloudislands.paper.activation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PendingSnapshotRecords {
    private final Map<UUID, PendingSnapshotRecord> pending = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSnapshotRecord> inFlight = new ConcurrentHashMap<>();

    void enqueue(PendingSnapshotRecord record) {
        pending.putIfAbsent(record.islandId(), record);
    }

    boolean contains(UUID islandId) {
        return pending.containsKey(islandId);
    }

    List<PendingSnapshotRecord> claimAll() {
        return pending.values().stream().filter(this::claim).toList();
    }

    List<PendingSnapshotRecord> claim(UUID islandId) {
        PendingSnapshotRecord record = pending.get(islandId);
        return record != null && claim(record) ? List.of(record) : List.of();
    }

    void completed(PendingSnapshotRecord record) {
        pending.remove(record.islandId(), record);
        inFlight.remove(record.islandId(), record);
    }

    void failed(PendingSnapshotRecord record) {
        inFlight.remove(record.islandId(), record);
    }

    int size() {
        return pending.size();
    }

    private boolean claim(PendingSnapshotRecord record) {
        return inFlight.putIfAbsent(record.islandId(), record) == null;
    }

    record PendingSnapshotRecord(
        UUID islandId,
        long snapshotNo,
        String storagePath,
        String reason,
        String checksum,
        long sizeBytes,
        String nodeId,
        long fencingToken
    ) {}
}
