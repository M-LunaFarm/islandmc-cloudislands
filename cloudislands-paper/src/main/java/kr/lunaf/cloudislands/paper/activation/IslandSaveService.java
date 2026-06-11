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

public final class IslandSaveService {
    private final IslandStorage storage;
    private final IslandBundleExporter exporter;
    private final Path exportRoot;

    public IslandSaveService(IslandStorage storage, IslandBundleExporter exporter, Path exportRoot) {
        this.storage = storage;
        this.exporter = exporter;
        this.exportRoot = exportRoot;
    }

    public SaveResult save(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        IslandBundleExporter.ExportedIslandBundle exported = exporter.export(islandId, activeIsland, exportRoot.resolve(islandId.toString()));
        IslandBundleManifest previous = storage.readManifest(islandId);
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
            previous.checksum()
        );
        try (InputStream input = Files.newInputStream(exported.bundleFile())) {
            storage.writeSnapshot(islandId, exported.snapshotNo(), input, manifest);
        }
        return new SaveResult(islandId, exported.snapshotNo(), exported.bundleFile());
    }

    public record SaveResult(UUID islandId, long snapshotNo, Path bundleFile) {}
}
