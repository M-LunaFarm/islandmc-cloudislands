package kr.lunaf.cloudislands.paper.world.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;

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
        Path worldPath = worldContainer.resolve(activeIsland.worldName());
        ProcessBuilder processBuilder = new ProcessBuilder("tar", "--zstd", "-cf", bundle.toAbsolutePath().toString(), "-C", worldPath.toAbsolutePath().toString(), ".");
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
}
