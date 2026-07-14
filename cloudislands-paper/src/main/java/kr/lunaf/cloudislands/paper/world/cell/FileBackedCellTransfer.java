package kr.lunaf.cloudislands.paper.world.cell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FileBackedCellTransfer {
    private final Path worldContainer;

    public FileBackedCellTransfer(Path worldContainer) {
        this.worldContainer = worldContainer;
    }

    public void place(CellPlacementPlan plan) throws IOException {
        Path worldRegion = worldRegion(plan.worldName());
        Files.createDirectories(worldRegion);
        replaceRegionFiles(plan.chunksDirectory(), worldRegion, plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
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

    private void replaceRegionFiles(Path source, Path target, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) throws IOException {
        int minRegionX = Math.floorDiv(minChunkX, 32);
        int maxRegionX = Math.floorDiv(maxChunkX, 32);
        int minRegionZ = Math.floorDiv(minChunkZ, 32);
        int maxRegionZ = Math.floorDiv(maxChunkZ, 32);
        Map<RegionCoordinate, Path> sourceFiles = sourceRegionFiles(source, minRegionX, maxRegionX, minRegionZ, maxRegionZ);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path transactionRoot = normalizedTarget.getParent().resolve(".cloudislands-cell-place-" + UUID.randomUUID());
        Path staged = transactionRoot.resolve("staged");
        Path backup = transactionRoot.resolve("backup");
        Set<RegionCoordinate> changed = new HashSet<>();
        Files.createDirectories(staged);
        Files.createDirectories(backup);
        try {
            for (Map.Entry<RegionCoordinate, Path> entry : sourceFiles.entrySet()) {
                Files.copy(entry.getValue(), staged.resolve(entry.getKey().fileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    RegionCoordinate coordinate = new RegionCoordinate(regionX, regionZ);
                    Path destination = normalizedTarget.resolve(coordinate.fileName());
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
                            throw new IOException("invalid destination region file: " + destination);
                        }
                        Files.move(destination, backup.resolve(coordinate.fileName()), StandardCopyOption.ATOMIC_MOVE);
                        changed.add(coordinate);
                    }
                    Path replacement = staged.resolve(coordinate.fileName());
                    if (Files.isRegularFile(replacement, LinkOption.NOFOLLOW_LINKS)) {
                        Files.move(replacement, destination, StandardCopyOption.ATOMIC_MOVE);
                        changed.add(coordinate);
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            rollbackReplacement(normalizedTarget, backup, changed, exception);
            try {
                deleteRecursively(transactionRoot);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
        try {
            deleteRecursively(transactionRoot);
        } catch (IOException ignored) {
            // Publishing is already committed; leftover hidden backup files are safer than reporting a false restore failure.
        }
    }

    private Map<RegionCoordinate, Path> sourceRegionFiles(Path source, int minRegionX, int maxRegionX, int minRegionZ, int maxRegionZ) throws IOException {
        Map<RegionCoordinate, Path> files = new HashMap<>();
        if (!Files.exists(source)) {
            return files;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in island region bundles: " + path);
                }
                RegionCoordinate coordinate = parseRegionCoordinate(path.getFileName().toString());
                if (coordinate == null) {
                    continue;
                }
                if (!coordinate.inside(minRegionX, maxRegionX, minRegionZ, maxRegionZ)) {
                    throw new IOException("bundle region coordinates do not match target cell: " + path.getFileName());
                }
                Path previous = files.putIfAbsent(coordinate, path);
                if (previous != null) {
                    throw new IOException("duplicate bundle region coordinate: " + path.getFileName());
                }
            }
        }
        return files;
    }

    private void rollbackReplacement(Path target, Path backup, Set<RegionCoordinate> changed, Exception original) {
        for (RegionCoordinate coordinate : changed) {
            Path destination = target.resolve(coordinate.fileName());
            Path previous = backup.resolve(coordinate.fileName());
            try {
                Files.deleteIfExists(destination);
                if (Files.exists(previous, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(previous, destination, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (IOException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
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
        private String fileName() {
            return "r." + regionX + "." + regionZ + ".mca";
        }

        private boolean inside(int minRegionX, int maxRegionX, int minRegionZ, int maxRegionZ) {
            return regionX >= minRegionX && regionX <= maxRegionX && regionZ >= minRegionZ && regionZ <= maxRegionZ;
        }
    }
}
