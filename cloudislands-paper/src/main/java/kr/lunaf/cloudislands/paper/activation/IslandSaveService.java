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

public final class IslandSaveService {
    private static final int RETAINED_SNAPSHOTS = 50;

    private final IslandStorage storage;
    private final IslandBundleExporter exporter;
    private final Path exportRoot;
    private final int retainedSnapshots;

    public IslandSaveService(IslandStorage storage, IslandBundleExporter exporter, Path exportRoot) {
        this(storage, exporter, exportRoot, RETAINED_SNAPSHOTS);
    }

    public IslandSaveService(IslandStorage storage, IslandBundleExporter exporter, Path exportRoot, int retainedSnapshots) {
        this.storage = storage;
        this.exporter = exporter;
        this.exportRoot = exportRoot;
        this.retainedSnapshots = Math.max(1, retainedSnapshots);
    }

    public SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true);
    }

    public SaveResult snapshotBeforeRestore(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true);
    }

    public SaveResult snapshotBeforeReset(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true);
    }

    public SaveResult snapshotBeforeMigration(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        return save(islandId, activeIsland, false, true);
    }

    public SaveResult backupBeforeDelete(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        SaveResult result = save(islandId, activeIsland, true, false);
        storage.deleteLiveState(islandId);
        return result;
    }

    private SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, boolean deleteBackup, boolean pruneAfterSave) throws IOException {
        IslandBundleExporter.ExportedIslandBundle exported = exporter.export(islandId, activeIsland, exportRoot.resolve(islandId.toString()));
        IslandBundleManifest previous = storage.readManifest(islandId);
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
            previous.schemaVersion(),
            previous.size(),
            new IslandLocation(activeIsland.worldName(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            previous.createdAt(),
            java.time.Instant.now(),
            checksum
        );
        try (InputStream input = Files.newInputStream(exported.bundleFile())) {
            if (deleteBackup) {
                storage.writeDeleteBackup(islandId, exported.snapshotNo(), input, manifest);
            } else {
                storage.writeSnapshot(islandId, exported.snapshotNo(), input, manifest);
            }
        }
        if (pruneAfterSave) {
            storage.pruneSnapshots(islandId, retainedSnapshots);
        }
        return new SaveResult(islandId, exported.snapshotNo(), exported.bundleFile(), checksum, sizeBytes);
    }

    public record SaveResult(UUID islandId, long snapshotNo, Path bundleFile, String checksum, long sizeBytes) {}
}
