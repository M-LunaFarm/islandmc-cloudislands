package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface IslandStorage {
    boolean available() throws IOException;
    IslandBundleManifest readManifest(UUID islandId) throws IOException;
    InputStream openLatestBundle(UUID islandId) throws IOException;
    InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException;
    InputStream openBundle(String storagePath) throws IOException;
    void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException;
    StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException;
    StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException;
    void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException;
    void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException;
    int pruneSnapshots(UUID islandId, int keepLatest) throws IOException;
    void deleteLiveState(UUID islandId) throws IOException;
    void deleteIsland(UUID islandId) throws IOException;

    record StoredBundle(String checksum, long sizeBytes) {}
}
