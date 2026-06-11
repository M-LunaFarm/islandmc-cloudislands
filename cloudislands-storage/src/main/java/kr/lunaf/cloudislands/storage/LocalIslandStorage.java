package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class LocalIslandStorage implements IslandStorage {
    private final Path root;

    public LocalIslandStorage(Path root) {
        this.root = root;
    }

    @Override
    public boolean available() throws IOException {
        Path islandsRoot = root.resolve("islands");
        Files.createDirectories(islandsRoot);
        return Files.isDirectory(islandsRoot) && Files.isWritable(islandsRoot);
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
    public InputStream openBundle(String storagePath) throws IOException {
        return Files.newInputStream(root.resolve(storagePath));
    }

    @Override
    public void writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path islandRoot = islandRoot(islandId);
        Path snapshotDir = islandRoot.resolve("snapshots").resolve(String.format("%06d", snapshotNo));
        writeBundle(islandRoot, snapshotDir, bundle, manifest, true);
    }

    @Override
    public void writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path islandRoot = islandRoot(islandId);
        Path backupDir = islandRoot.resolve("backups").resolve("delete-" + String.format("%06d", snapshotNo));
        writeBundle(islandRoot, backupDir, bundle, manifest, false);
    }

    @Override
    public void writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        IslandBundleManifest manifest = readManifest(islandId);
        try (InputStream input = openLatestBundle(islandId)) {
            writeDeleteBackup(islandId, snapshotNo, input, manifest);
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        Path islandRoot = islandRoot(islandId);
        String snapshot = String.format("%06d", snapshotNo);
        Path snapshotManifest = islandRoot.resolve("snapshots").resolve(snapshot).resolve("manifest.json");
        if (!Files.exists(snapshotManifest)) {
            throw new IOException("missing snapshot manifest: " + islandId + " #" + snapshotNo);
        }
        Files.writeString(islandRoot.resolve("manifest.json"), Files.readString(snapshotManifest, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), snapshot, StandardCharsets.UTF_8);
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        Path islandRoot = islandRoot(islandId);
        String snapshot = String.format("%06d", snapshotNo);
        Path sourceBundle = root.resolve(storagePath);
        Path sourceManifest = sourceBundle.resolveSibling("manifest.json");
        if (!Files.exists(sourceBundle) || !Files.exists(sourceManifest)) {
            throw new IOException("missing stored bundle: " + storagePath);
        }
        Path snapshotDir = islandRoot.resolve("snapshots").resolve(snapshot);
        Files.createDirectories(snapshotDir);
        Files.copy(sourceBundle, snapshotDir.resolve("bundle.tar.zst"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(sourceManifest, snapshotDir.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        Path sourceChecksums = sourceBundle.resolveSibling("checksums.sha256");
        if (Files.exists(sourceChecksums)) {
            Files.copy(sourceChecksums, snapshotDir.resolve("checksums.sha256"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(islandRoot.resolve("manifest.json"), Files.readString(snapshotDir.resolve("manifest.json"), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), snapshot, StandardCharsets.UTF_8);
    }

    private void writeBundle(Path islandRoot, Path snapshotDir, InputStream bundle, IslandBundleManifest manifest, boolean updateLatest) throws IOException {
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
        if (updateLatest) {
            Files.writeString(islandRoot.resolve("manifest.json"), IslandManifestJson.write(savedManifest), StandardCharsets.UTF_8);
            Files.writeString(islandRoot.resolve("latest"), snapshotDir.getFileName().toString(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) throws IOException {
        if (keepLatest < 1) {
            throw new IllegalArgumentException("keepLatest must be positive");
        }
        Path snapshotsRoot = islandRoot(islandId).resolve("snapshots");
        if (!Files.exists(snapshotsRoot)) {
            return 0;
        }
        List<Path> snapshots;
        try (var stream = Files.list(snapshotsRoot)) {
            snapshots = stream
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .toList();
        }
        int deleted = 0;
        for (int index = keepLatest; index < snapshots.size(); index++) {
            deleteRecursively(snapshots.get(index));
            deleted++;
        }
        return deleted;
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        deleteRecursively(islandRoot(islandId));
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private Path islandRoot(UUID islandId) {
        return root.resolve("islands").resolve(islandId.toString());
    }
}
