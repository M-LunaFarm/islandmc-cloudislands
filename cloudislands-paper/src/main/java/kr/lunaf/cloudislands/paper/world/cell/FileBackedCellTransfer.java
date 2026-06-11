package kr.lunaf.cloudislands.paper.world.cell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileBackedCellTransfer {
    private final Path worldContainer;

    public FileBackedCellTransfer(Path worldContainer) {
        this.worldContainer = worldContainer;
    }

    public void place(CellPlacementPlan plan) throws IOException {
        Path worldRegion = worldContainer.resolve(plan.worldName()).resolve("region");
        Files.createDirectories(worldRegion);
        copyDirectory(plan.chunksDirectory(), worldRegion);
    }

    public void extract(CellExtractionPlan plan) throws IOException {
        Path worldRegion = worldContainer.resolve(plan.worldName()).resolve("region");
        Files.createDirectories(plan.targetChunksDirectory());
        copyDirectory(worldRegion, plan.targetChunksDirectory());
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
