package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class LocalIslandStorage implements IslandStorage {
    private final Path root;

    public LocalIslandStorage(Path root) {
        this.root = root;
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        Path manifest = root.resolve("islands").resolve(islandId.toString()).resolve("manifest.json");
        if (!Files.exists(manifest)) {
            throw new IOException("missing island manifest: " + islandId);
        }
        return null;
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        return Files.newInputStream(root.resolve("islands").resolve(islandId.toString()).resolve("latest"));
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path snapshotDir = root.resolve("islands").resolve(islandId.toString()).resolve("snapshots").resolve(String.format("%06d", snapshotNo));
        Files.createDirectories(snapshotDir);
        Files.copy(bundle, snapshotDir.resolve("bundle.tar.zst"));
    }
}
