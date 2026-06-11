package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
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
        return IslandManifestJson.read(Files.readString(manifest, StandardCharsets.UTF_8));
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        String latest = Files.readString(islandRoot(islandId).resolve("latest"), StandardCharsets.UTF_8).trim();
        return Files.newInputStream(islandRoot(islandId).resolve("snapshots").resolve(latest).resolve("bundle.tar.zst"));
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        return Files.newInputStream(islandRoot(islandId).resolve("snapshots").resolve(String.format("%06d", snapshotNo)).resolve("bundle.tar.zst"));
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path islandRoot = islandRoot(islandId);
        Path snapshotDir = islandRoot.resolve("snapshots").resolve(String.format("%06d", snapshotNo));
        Files.createDirectories(snapshotDir);
        Path snapshotBundle = snapshotDir.resolve("bundle.tar.zst");
        Files.copy(bundle, snapshotBundle, StandardCopyOption.REPLACE_EXISTING);
        String actualChecksum;
        try (InputStream input = Files.newInputStream(snapshotBundle)) {
            actualChecksum = Sha256Checksums.of(input);
        }
        IslandBundleManifest savedManifest = new IslandBundleManifest(
            manifest.islandId(),
            manifest.ownerUuid(),
            manifest.formatVersion(),
            manifest.minecraftVersion(),
            manifest.schemaVersion(),
            manifest.size(),
            manifest.spawn(),
            manifest.createdAt(),
            manifest.savedAt(),
            actualChecksum
        );
        Files.writeString(snapshotDir.resolve("manifest.json"), IslandManifestJson.write(savedManifest), StandardCharsets.UTF_8);
        Files.writeString(snapshotDir.resolve("checksums.sha256"), actualChecksum + "  bundle.tar.zst\n", StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("manifest.json"), IslandManifestJson.write(savedManifest), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), snapshotDir.getFileName().toString(), StandardCharsets.UTF_8);
    }

    private Path islandRoot(UUID islandId) {
        return root.resolve("islands").resolve(islandId.toString());
    }
}
