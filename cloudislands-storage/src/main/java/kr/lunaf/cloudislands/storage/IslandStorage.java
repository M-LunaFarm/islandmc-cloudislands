package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public interface IslandStorage {
    boolean available() throws IOException;
    IslandBundleManifest readManifest(UUID islandId) throws IOException;
    InputStream openLatestBundle(UUID islandId) throws IOException;
    InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException;
    InputStream openBundle(String storagePath) throws IOException;
    StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException;
    StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException;
    StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException;
    default StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo, String reason) throws IOException {
        return writeDeleteBackupFromLatest(islandId, snapshotNo);
    }
    void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException;
    void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException;
    int pruneSnapshots(UUID islandId, int keepLatest) throws IOException;
    default int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) throws IOException {
        SnapshotRetentionPolicy effectivePolicy = policy == null ? SnapshotRetentionPolicy.defaultPolicy() : policy.normalized();
        return pruneSnapshots(islandId, effectivePolicy.retainedSnapshotCount());
    }
    void deleteLiveState(UUID islandId) throws IOException;
    void deleteIsland(UUID islandId) throws IOException;

    record StoredBundle(String checksum, long sizeBytes, String storagePath, String checksumAlgorithm, String compression) {
        public StoredBundle(String checksum, long sizeBytes) {
            this(checksum, sizeBytes, "", "SHA-256", "zstd");
        }
    }
}
