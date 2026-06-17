package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.paper.world.export.IslandBundleExporter;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class IslandSaveService {
    private static final int RETAINED_SNAPSHOTS = 50;

    private final IslandStorage storage;
    private final IslandBundleExporter exporter;
    private final Path exportRoot;
    private final SnapshotRetentionPolicy retentionPolicy;

    public IslandSaveService(IslandStorage storage, IslandBundleExporter exporter, Path exportRoot) {
        this(storage, exporter, exportRoot, RETAINED_SNAPSHOTS);
    }

    public IslandSaveService(IslandStorage storage, IslandBundleExporter exporter, Path exportRoot, int retainedSnapshots) {
        this(storage, exporter, exportRoot, new SnapshotRetentionPolicy(retainedSnapshots, 0, 0, 0, true, "SHA-256"));
    }

    public IslandSaveService(IslandStorage storage, IslandBundleExporter exporter, Path exportRoot, SnapshotRetentionPolicy retentionPolicy) {
        this.storage = storage;
        this.exporter = exporter;
        this.exportRoot = exportRoot;
        this.retentionPolicy = (retentionPolicy == null ? SnapshotRetentionPolicy.defaultPolicy() : retentionPolicy).normalized();
    }

    public SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, null, "AUTO");
    }

    public SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, IslandBundleManifest baseManifest) throws IOException {
        return save(islandId, activeIsland, baseManifest, "AUTO");
    }

    public SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, IslandBundleManifest baseManifest, String reason) throws IOException {
        return save(islandId, activeIsland, false, true, baseManifest, reason);
    }

    public SaveResult snapshotBeforeRestore(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true, null, "BEFORE_RESTORE");
    }

    public SaveResult snapshotBeforeReset(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true, null, "BEFORE_RESET");
    }

    public SaveResult snapshotBeforeMigration(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true, null, "BEFORE_MIGRATION");
    }

    public SaveResult backupBeforeDelete(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        SaveResult result = save(islandId, activeIsland, true, false, null, "BEFORE_DELETE");
        storage.deleteLiveState(islandId);
        return result;
    }

    private SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, boolean deleteBackup, boolean pruneAfterSave, IslandBundleManifest baseManifest, String reason) throws IOException {
        if (!retentionPolicy.checksumAlgorithm().equalsIgnoreCase("SHA-256")) {
            throw new IOException("unsupported snapshot checksum algorithm: " + retentionPolicy.checksumAlgorithm());
        }
        if (!retentionPolicy.compress()) {
            throw new IOException("uncompressed snapshot bundles are not supported by the current storage format");
        }
        IslandBundleManifest previous = (baseManifest == null ? storage.readManifest(islandId) : baseManifest).withSnapshotReason(reason);
        IslandBundleExporter.ExportedIslandBundle exported = exporter.export(islandId, activeIsland, exportRoot.resolve(islandId.toString()), previous);
        long sizeBytes = Files.size(exported.bundleFile());
        String checksum;
        try (InputStream input = Files.newInputStream(exported.bundleFile())) {
            checksum = Sha256Checksums.of(input);
        }
        IslandBundleManifest manifest = new IslandBundleManifest(
            islandId,
            previous.ownerUuid(),
            previous.formatVersion(),
            previous.minecraftVersion(),
            (int) activeIsland.schemaVersion(),
            activeIsland.islandSize(),
            new IslandLocation(activeIsland.worldName(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            previous.homes(),
            previous.warps(),
            previous.biomes(),
            previous.createdAt(),
            java.time.Instant.now(),
            checksum,
            "SHA-256",
            "zstd",
            previous.storagePath(),
            previous.sizeBytes(),
            reason,
            previous.portable(),
            previous.placementPolicy(),
            previous.restorePolicy()
        );
        IslandStorage.StoredBundle storedBundle;
        try (InputStream input = Files.newInputStream(exported.bundleFile())) {
            if (deleteBackup) {
                storedBundle = storage.writeDeleteBackup(islandId, exported.snapshotNo(), input, manifest);
            } else {
                storedBundle = storage.writeSnapshot(islandId, exported.snapshotNo(), input, manifest);
            }
        }
        if (pruneAfterSave) {
            storage.pruneSnapshots(islandId, retentionPolicy);
        }
        return new SaveResult(islandId, exported.snapshotNo(), exported.bundleFile(), storedBundle.storagePath(), storedBundle.checksum(), storedBundle.sizeBytes());
    }

    public record SaveResult(UUID islandId, long snapshotNo, Path bundleFile, String storagePath, String checksum, long sizeBytes) {}
}
