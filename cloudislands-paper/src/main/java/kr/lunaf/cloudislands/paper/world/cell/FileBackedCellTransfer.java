package kr.lunaf.cloudislands.paper.world.cell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileBackedCellTransfer {
    private final Path worldContainer;

    public FileBackedCellTransfer(Path worldContainer) {
        this.worldContainer = worldContainer;
    }

    public void place(CellPlacementPlan plan) throws IOException {
        Path worldRegion = worldRegion(plan.worldName());
        Files.createDirectories(worldRegion);
        copyRegionFiles(plan.chunksDirectory(), worldRegion, plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
    }

    public void extract(CellExtractionPlan plan) throws IOException {
        Path worldRegion = worldRegion(plan.worldName());
        Files.createDirectories(plan.targetChunksDirectory());
        copyRegionFiles(worldRegion, plan.targetChunksDirectory(), plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
    }

    private Path worldRegion(String worldName) throws IOException {
        if (worldName == null || worldName.isBlank() || worldName.contains("/") || worldName.contains("\\") || worldName.contains("..")) {
            throw new IOException("invalid world name: " + worldName);
        }
        Path root = worldContainer.toAbsolutePath().normalize();
        Path region = root.resolve(worldName).resolve("region").normalize();
        if (!region.startsWith(root)) {
            throw new IOException("world region escapes container: " + worldName);
        }
        return region;
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
            Path normalizedTarget = target.toAbsolutePath().normalize();
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in island region bundles: " + path);
                }
                RegionCoordinate coordinate = parseRegionCoordinate(path.getFileName().toString());
                if (coordinate == null || !coordinate.inside(minRegionX, maxRegionX, minRegionZ, maxRegionZ)) {
                    continue;
                }
                Path relative = source.relativize(path);
                Path destination = normalizedTarget.resolve(relative).normalize();
                if (!destination.startsWith(normalizedTarget)) {
                    throw new IOException("region copy target escapes directory: " + relative);
                }
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
