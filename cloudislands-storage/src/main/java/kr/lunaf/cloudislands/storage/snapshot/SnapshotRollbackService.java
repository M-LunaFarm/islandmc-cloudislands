package kr.lunaf.cloudislands.storage.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class SnapshotRollbackService {
    private final IslandStorage storage;

    public SnapshotRollbackService(IslandStorage storage) {
        this.storage = storage;
    }

    public RollbackPlan plan(UUID islandId, long snapshotNo) throws IOException {
        IslandBundleManifest current = storage.readManifest(islandId);
        return new RollbackPlan(islandId, snapshotNo, current.schemaVersion(), SnapshotReason.BEFORE_RESTORE);
    }

    public void writePreRestoreSnapshot(UUID islandId, InputStream bundle, IslandBundleManifest manifest, long snapshotNo) throws IOException {
        storage.writeSnapshot(islandId, snapshotNo, bundle, manifest);
    }

    public record RollbackPlan(UUID islandId, long targetSnapshotNo, int schemaVersion, SnapshotReason preRestoreReason) {}
}
