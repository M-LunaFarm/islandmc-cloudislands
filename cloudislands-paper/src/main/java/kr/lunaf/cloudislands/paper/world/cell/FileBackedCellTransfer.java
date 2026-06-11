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
        copyRegionFiles(plan.chunksDirectory(), worldRegion, plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
    }

    public void extract(CellExtractionPlan plan) throws IOException {
        Path worldRegion = worldContainer.resolve(plan.worldName()).resolve("region");
        Files.createDirectories(plan.targetChunksDirectory());
        copyRegionFiles(worldRegion, plan.targetChunksDirectory(), plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
    }

    private void copyRegionFiles(Path source, Path target, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        int minRegionX = Math.floorDiv(minChunkX, 32);
        int maxRegionX = Math.floorDiv(maxChunkX, 32);
        int minRegionZ = Math.floorDiv(minChunkZ, 32);
        int maxRegionZ = Math.floorDiv(maxChunkZ, 32);
        try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                RegionCoordinate coordinate = parseRegionCoordinate(path.getFileName().toString());
                if (coordinate == null || !coordinate.inside(minRegionX, maxRegionX, minRegionZ, maxRegionZ)) {
                    continue;
                }
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private RegionCoordinate parseRegionCoordinate(String fileName) {
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
            return null;
        }
        String[] parts = fileName.substring(2, fileName.length() - 4).split("\\.");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new RegionCoordinate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record RegionCoordinate(int regionX, int regionZ) {
        private boolean inside(int minRegionX, int maxRegionX, int minRegionZ, int maxRegionZ) {
            return regionX >= minRegionX && regionX <= maxRegionX && regionZ >= minRegionZ && regionZ <= maxRegionZ;
        }
    }
}
