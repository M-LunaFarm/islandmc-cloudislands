package kr.lunaf.cloudislands.paper.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class FallbackIslandStorage implements IslandStorage {
    private final IslandStorage primary;
    private final IslandStorage fallback;
    private final Logger logger;

    public FallbackIslandStorage(IslandStorage primary, IslandStorage fallback, Logger logger) {
        this.primary = primary;
        this.fallback = fallback;
        this.logger = logger;
    }

    @Override
    public boolean available() throws IOException {
        IOException primaryFailure = null;
        try {
            if (primary.available()) {
                return true;
            }
        } catch (IOException exception) {
            primaryFailure = exception;
            warn("Primary island storage health check failed, checking fallback", exception);
        }
        try {
            return fallback.available();
        } catch (IOException fallbackFailure) {
            if (primaryFailure != null) {
                fallbackFailure.addSuppressed(primaryFailure);
            }
            throw fallbackFailure;
        }
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        try {
            return primary.readManifest(islandId);
        } catch (IOException exception) {
            warn("Primary island manifest read failed, using fallback for " + islandId, exception);
            return fallback.readManifest(islandId);
        }
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        try {
            return primary.openLatestBundle(islandId);
        } catch (IOException exception) {
            warn("Primary latest island bundle read failed, using fallback for " + islandId, exception);
            return fallback.openLatestBundle(islandId);
        }
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        try {
            return primary.openSnapshotBundle(islandId, snapshotNo);
        } catch (IOException exception) {
            warn("Primary island snapshot read failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            return fallback.openSnapshotBundle(islandId, snapshotNo);
        }
    }

    @Override
    public InputStream openBundle(String storagePath) throws IOException {
        try {
            return primary.openBundle(storagePath);
        } catch (IOException exception) {
            warn("Primary island bundle path read failed, using fallback for " + storagePath, exception);
            return fallback.openBundle(storagePath);
        }
    }

    @Override
    public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        byte[] bytes = bundle.readAllBytes();
        try {
            StoredBundle stored = primary.writeSnapshot(islandId, snapshotNo, new ByteArrayInputStream(bytes), manifest);
            mirrorSnapshot(islandId, snapshotNo, bytes, manifest);
            return stored;
        } catch (IOException exception) {
            warn("Primary island snapshot write failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            return fallback.writeSnapshot(islandId, snapshotNo, new ByteArrayInputStream(bytes), manifest);
        }
    }

    @Override
    public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        byte[] bytes = bundle.readAllBytes();
        try {
            StoredBundle stored = primary.writeDeleteBackup(islandId, snapshotNo, new ByteArrayInputStream(bytes), manifest);
            mirrorDeleteBackup(islandId, snapshotNo, bytes, manifest);
            return stored;
        } catch (IOException exception) {
            warn("Primary island delete backup write failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            return fallback.writeDeleteBackup(islandId, snapshotNo, new ByteArrayInputStream(bytes), manifest);
        }
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        return writeDeleteBackupFromLatest(islandId, snapshotNo, "BEFORE_DELETE");
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo, String reason) throws IOException {
        IslandBundleManifest manifest = readManifest(islandId).withSnapshotReason(reason);
        try (InputStream input = openLatestBundle(islandId)) {
            return writeDeleteBackup(islandId, snapshotNo, input, manifest);
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        try {
            primary.promoteSnapshot(islandId, snapshotNo);
            mirrorPromoteSnapshot(islandId, snapshotNo);
        } catch (IOException exception) {
            warn("Primary island snapshot promote failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            fallback.promoteSnapshot(islandId, snapshotNo);
        }
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        try {
            primary.promoteBundle(islandId, snapshotNo, storagePath);
            mirrorPromoteBundle(islandId, snapshotNo, storagePath);
        } catch (IOException exception) {
            warn("Primary island bundle promote failed, using fallback for " + islandId + " #" + snapshotNo, exception);
            fallback.promoteBundle(islandId, snapshotNo, storagePath);
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) throws IOException {
        try {
            int pruned = primary.pruneSnapshots(islandId, keepLatest);
            mirrorPruneSnapshots(islandId, keepLatest);
            return pruned;
        } catch (IOException exception) {
            warn("Primary island snapshot prune failed, using fallback for " + islandId, exception);
            return fallback.pruneSnapshots(islandId, keepLatest);
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) throws IOException {
        try {
            int pruned = primary.pruneSnapshots(islandId, policy);
            mirrorPruneSnapshots(islandId, policy);
            return pruned;
        } catch (IOException exception) {
            warn("Primary island retention prune failed, using fallback for " + islandId, exception);
            return fallback.pruneSnapshots(islandId, policy);
        }
    }

    @Override
    public void deleteLiveState(UUID islandId) throws IOException {
        try {
            primary.deleteLiveState(islandId);
            mirrorDeleteLiveState(islandId);
        } catch (IOException exception) {
            warn("Primary island live state delete failed, using fallback for " + islandId, exception);
            fallback.deleteLiveState(islandId);
        }
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        try {
            primary.deleteIsland(islandId);
            mirrorDeleteIsland(islandId);
        } catch (IOException exception) {
            warn("Primary island delete failed, using fallback for " + islandId, exception);
            fallback.deleteIsland(islandId);
        }
    }

    private void mirrorSnapshot(UUID islandId, long snapshotNo, byte[] bytes, IslandBundleManifest manifest) {
        try {
            fallback.writeSnapshot(islandId, snapshotNo, new ByteArrayInputStream(bytes), manifest);
        } catch (IOException exception) {
            warn("Fallback island snapshot mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorDeleteBackup(UUID islandId, long snapshotNo, byte[] bytes, IslandBundleManifest manifest) {
        try {
            fallback.writeDeleteBackup(islandId, snapshotNo, new ByteArrayInputStream(bytes), manifest);
        } catch (IOException exception) {
            warn("Fallback island delete backup mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorPromoteSnapshot(UUID islandId, long snapshotNo) {
        try {
            fallback.promoteSnapshot(islandId, snapshotNo);
        } catch (IOException exception) {
            warn("Fallback island snapshot promote mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorPromoteBundle(UUID islandId, long snapshotNo, String storagePath) {
        try {
            fallback.promoteBundle(islandId, snapshotNo, storagePath);
        } catch (IOException exception) {
            warn("Fallback island bundle promote mirror failed for " + islandId + " #" + snapshotNo, exception);
        }
    }

    private void mirrorPruneSnapshots(UUID islandId, int keepLatest) {
        try {
            fallback.pruneSnapshots(islandId, keepLatest);
        } catch (IOException exception) {
            warn("Fallback island snapshot prune mirror failed for " + islandId, exception);
        }
    }

    private void mirrorPruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) {
        try {
            fallback.pruneSnapshots(islandId, policy);
        } catch (IOException exception) {
            warn("Fallback island retention prune mirror failed for " + islandId, exception);
        }
    }

    private void mirrorDeleteLiveState(UUID islandId) {
        try {
            fallback.deleteLiveState(islandId);
        } catch (IOException exception) {
            warn("Fallback island live state delete mirror failed for " + islandId, exception);
        }
    }

    private void mirrorDeleteIsland(UUID islandId) {
        try {
            fallback.deleteIsland(islandId);
        } catch (IOException exception) {
            warn("Fallback island delete mirror failed for " + islandId, exception);
        }
    }

    private void warn(String message, IOException exception) {
        if (logger != null) {
            logger.warning(message + ": " + exception.getMessage());
        }
    }
}
