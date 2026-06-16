package kr.lunaf.cloudislands.paper.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlan;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;

public final class IslandWorldRestorer {
    private final IslandStorage storage;
    private final Path stagingRoot;
    private final BundleRestorePlanner restorePlanner;

    public IslandWorldRestorer(IslandStorage storage, Path stagingRoot, BundleRestorePlanner restorePlanner) {
        this.storage = storage;
        this.stagingRoot = stagingRoot;
        this.restorePlanner = restorePlanner;
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ) throws IOException {
        return stage(islandId, worldName, originX, originZ, 0L);
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ, long snapshotNo) throws IOException {
        return stage(islandId, worldName, originX, originZ, snapshotNo, "");
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ, long snapshotNo, String storagePath) throws IOException {
        Path islandStage = stagingRoot.resolve(islandId.toString());
        Files.createDirectories(islandStage);
        Path bundle = islandStage.resolve("bundle.tar.zst");
        try (InputStream input = storagePath == null || storagePath.isBlank() ? snapshotNo > 0L ? storage.openSnapshotBundle(islandId, snapshotNo) : storage.openLatestBundle(islandId) : storage.openBundle(storagePath)) {
            Files.copy(input, bundle, StandardCopyOption.REPLACE_EXISTING);
        }
        verifyStagedBundle(islandId, bundle, snapshotNo, storagePath);
        RestorePlan restorePlan = new RestorePlan(islandId, worldName, originX, originZ, bundle);
        return restorePlanner.plan(restorePlan);
    }

    private void verifyStagedBundle(UUID islandId, Path bundle, long snapshotNo, String storagePath) throws IOException {
        Optional<IslandBundleManifest> manifest = restoreManifest(islandId, snapshotNo, storagePath);
        if (manifest.isEmpty()) {
            return;
        }
        IslandBundleManifest restoreManifest = manifest.get();
        validateRestoreManifest(islandId, restoreManifest);
        String expectedChecksum = restoreManifest.checksum();
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return;
        }
        String actualChecksum;
        try (InputStream input = Files.newInputStream(bundle)) {
            actualChecksum = Sha256Checksums.of(input);
        }
        if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            throw new IOException("island bundle checksum mismatch: " + islandId);
        }
    }

    private void validateRestoreManifest(UUID islandId, IslandBundleManifest manifest) throws IOException {
        if (!manifest.portable()) {
            throw new IOException("island bundle is not portable: " + islandId);
        }
        if (!supportedChecksumAlgorithm(manifest.checksumAlgorithm())) {
            throw new IOException("unsupported island bundle checksum algorithm: " + manifest.checksumAlgorithm());
        }
        if (!supportedCompression(manifest.compression())) {
            throw new IOException("unsupported island bundle compression: " + manifest.compression());
        }
    }

    private boolean supportedChecksumAlgorithm(String algorithm) {
        return algorithm == null || algorithm.isBlank() || algorithm.equalsIgnoreCase("SHA-256");
    }

    private boolean supportedCompression(String compression) {
        return compression == null || compression.isBlank() || compression.equalsIgnoreCase("zstd");
    }

    private Optional<IslandBundleManifest> restoreManifest(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        if (storagePath != null && !storagePath.isBlank()) {
            return storage.readBundleManifest(storagePath);
        }
        if (snapshotNo > 0L) {
            return storage.readSnapshotManifest(islandId, snapshotNo);
        }
        return Optional.of(storage.readManifest(islandId));
    }

    public record RestorePlan(UUID islandId, String worldName, int originX, int originZ, Path stagedBundle) {}
}
