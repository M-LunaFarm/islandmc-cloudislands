package kr.lunaf.cloudislands.paper.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlan;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
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
        verifyLatestBundle(islandId, bundle, snapshotNo, storagePath);
        RestorePlan restorePlan = new RestorePlan(islandId, worldName, originX, originZ, bundle);
        return restorePlanner.plan(restorePlan);
    }

    private void verifyLatestBundle(UUID islandId, Path bundle, long snapshotNo, String storagePath) throws IOException {
        if (snapshotNo > 0L || (storagePath != null && !storagePath.isBlank())) {
            return;
        }
        String expectedChecksum = storage.readManifest(islandId).checksum();
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return;
        }
        String actualChecksum;
        try (InputStream input = Files.newInputStream(bundle)) {
            actualChecksum = Sha256Checksums.of(input);
        }
        if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            throw new IOException("latest island bundle checksum mismatch: " + islandId);
        }
    }

    public record RestorePlan(UUID islandId, String worldName, int originX, int originZ, Path stagedBundle) {}
}
