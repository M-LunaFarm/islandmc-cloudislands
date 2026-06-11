package kr.lunaf.cloudislands.paper.world.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.world.cell.CellExtractionPlan;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.cell.ShardCellTransferPlanner;

public final class ExternalTarIslandBundleExporter implements IslandBundleExporter {
    private final Path worldContainer;

    public ExternalTarIslandBundleExporter(Path worldContainer) {
        this.worldContainer = worldContainer;
    }

    @Override
    public ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);
        long snapshotNo = Instant.now().toEpochMilli();
        Path bundle = targetDirectory.resolve(String.format("%d-bundle.tar.zst", snapshotNo));
        Path staging = targetDirectory.resolve("cell-stage");
        deleteDirectory(staging);
        Files.createDirectories(staging);
        CellExtractionPlan extraction = new ShardCellTransferPlanner(activeIsland.islandSize())
            .extraction(islandId, activeIsland.worldName(), activeIsland.originX(), activeIsland.originZ(), staging.resolve("chunks"));
        new FileBackedCellTransfer(worldContainer).extract(extraction);
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
