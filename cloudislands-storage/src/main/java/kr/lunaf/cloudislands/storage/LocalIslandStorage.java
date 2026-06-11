package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class LocalIslandStorage implements IslandStorage {
    private final Path root;

    public LocalIslandStorage(Path root) {
        this.root = root;
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        Path manifest = islandRoot(islandId).resolve("manifest.json");
        if (!Files.exists(manifest)) {
            throw new IOException("missing island manifest: " + islandId);
        }
        return IslandManifestJson.minimal(islandId, new UUID(0L, 0L), "unknown");
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        return Files.newInputStream(islandRoot(islandId).resolve("latest"));
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path islandRoot = islandRoot(islandId);
        Path snapshotDir = islandRoot.resolve("snapshots").resolve(String.format("%06d", snapshotNo));
        Files.createDirectories(snapshotDir);
        Path snapshotBundle = snapshotDir.resolve("bundle.tar.zst");
        Files.copy(bundle, snapshotBundle, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(snapshotDir.resolve("manifest.json"), IslandManifestJson.write(manifest), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("manifest.json"), IslandManifestJson.write(manifest), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), snapshotDir.getFileName().toString(), StandardCharsets.UTF_8);
    }

    private Path islandRoot(UUID islandId) {
        return root.resolve("islands").resolve(islandId.toString());
    }
}
