package kr.lunaf.cloudislands.paper.world.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.world.cell.CellExtractionPlan;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.cell.ShardCellTransferPlanner;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class ExternalTarIslandBundleExporter implements IslandBundleExporter {
    private final Path worldContainer;

    public ExternalTarIslandBundleExporter(Path worldContainer) {
        this.worldContainer = worldContainer;
    }

    @Override
    public ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory) throws IOException {
        return export(islandId, activeIsland, targetDirectory, null);
    }

    @Override
    public ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory, IslandBundleManifest manifest) throws IOException {
        Files.createDirectories(targetDirectory);
        long snapshotNo = Instant.now().toEpochMilli();
        Path bundle = targetDirectory.resolve(String.format("%d-bundle.tar.zst", snapshotNo));
        while (Files.exists(bundle)) {
            snapshotNo++;
            bundle = targetDirectory.resolve(String.format("%d-bundle.tar.zst", snapshotNo));
        }
        Path staging = targetDirectory.resolve("cell-stage");
        deleteDirectory(staging);
        Files.createDirectories(staging);
        CellExtractionPlan extraction = new ShardCellTransferPlanner(activeIsland.islandSize())
            .extraction(islandId, activeIsland.worldName(), activeIsland.originX(), activeIsland.originZ(), staging.resolve("chunks"));
        new FileBackedCellTransfer(worldContainer).extract(extraction);
        writeStagedManifest(islandId, activeIsland, staging.resolve("manifest.json"), manifest);
        ProcessBuilder processBuilder = new ProcessBuilder("tar", "--zstd", "-cf", bundle.toAbsolutePath().toString(), "-C", staging.toAbsolutePath().toString(), ".");
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("bundle export failed with exit code " + exitCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("bundle export interrupted", exception);
        }
        return new ExportedIslandBundle(islandId, bundle, snapshotNo);
    }

    private void writeStagedManifest(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path manifestPath, IslandBundleManifest source) throws IOException {
        Instant now = Instant.now();
        IslandBundleManifest manifest = new IslandBundleManifest(
            islandId,
            source == null ? new UUID(0L, 0L) : source.ownerUuid(),
            source == null ? 3 : source.formatVersion(),
            source == null ? "unknown" : source.minecraftVersion(),
            source == null ? activeIsland.schemaVersion() : source.schemaVersion(),
            source == null ? activeIsland.islandSize() : source.size(),
            new IslandLocation(activeIsland.worldName(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            source == null ? now : source.createdAt(),
            now,
            ""
        );
        Files.writeString(manifestPath, IslandManifestJson.write(manifest), StandardCharsets.UTF_8);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
