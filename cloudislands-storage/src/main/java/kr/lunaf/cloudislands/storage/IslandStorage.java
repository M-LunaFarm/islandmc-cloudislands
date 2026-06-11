package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface IslandStorage {
    boolean available() throws IOException;
    IslandBundleManifest readManifest(UUID islandId) throws IOException;
    InputStream openLatestBundle(UUID islandId) throws IOException;
    InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException;
    void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException;
    int pruneSnapshots(UUID islandId, int keepLatest) throws IOException;
    void deleteIsland(UUID islandId) throws IOException;
}
