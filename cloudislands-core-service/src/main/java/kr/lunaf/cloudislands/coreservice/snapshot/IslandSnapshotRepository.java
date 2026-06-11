package kr.lunaf.cloudislands.coreservice.snapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

public interface IslandSnapshotRepository {
    IslandSnapshotRecord record(UUID islandId, long snapshotNo, String storagePath, String reason, UUID createdBy, String checksum, long sizeBytes);
    List<IslandSnapshotRecord> list(UUID islandId, int limit);
    Optional<IslandSnapshotRecord> find(UUID islandId, long snapshotNo);
}
