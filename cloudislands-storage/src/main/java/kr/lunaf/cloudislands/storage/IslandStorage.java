package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface IslandStorage {
    IslandBundleManifest readManifest(UUID islandId) throws IOException;
    InputStream openLatestBundle(UUID islandId) throws IOException;
    void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException;
}
