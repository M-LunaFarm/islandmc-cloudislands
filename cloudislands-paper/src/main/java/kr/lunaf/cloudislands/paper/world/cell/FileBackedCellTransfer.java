package kr.lunaf.cloudislands.paper.world.cell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class FileBackedCellTransfer {
    private final Path worldContainer;

    public FileBackedCellTransfer(Path worldContainer) {
        this.worldContainer = worldContainer;
    }

    public void place(CellPlacementPlan plan) throws IOException {
        Path world = worldDirectory(plan.worldName());
        replaceRegionFiles(plan, world, false);
    }

    public void clear(String worldName, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) throws IOException {
        Path unused = worldContainer.resolve(".cloudislands-empty-cell-source");
        CellPlacementPlan plan = new CellPlacementPlan(UUID.randomUUID(), worldName, 0, 0, unused, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        replaceRegionFiles(plan, worldDirectory(worldName), true);
    }

    public void extract(CellExtractionPlan plan) throws IOException {
        Path world = worldDirectory(plan.worldName());
        Files.createDirectories(plan.targetChunksDirectory());
        Files.createDirectories(plan.targetEntitiesDirectory());
        Files.createDirectories(plan.targetPoiDirectory());
        copyRegionFiles(world.resolve("region"), plan.targetChunksDirectory(), plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
        copyRegionFiles(world.resolve("entities"), plan.targetEntitiesDirectory(), plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
        copyRegionFiles(world.resolve("poi"), plan.targetPoiDirectory(), plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
    }

    private Path worldDirectory(String worldName) throws IOException {
        if (worldName == null || worldName.isBlank() || worldName.contains("/") || worldName.contains("\\") || worldName.contains("..")) {
            throw new IOException("invalid world name: " + worldName);
        }
        Path root = worldContainer.toAbsolutePath().normalize();
        Path modernWorld = modernDimensionDirectory(root, worldName);
        Path legacyWorld = root.resolve(worldName).normalize();
        Path world = modernWorld != null && (Files.isDirectory(modernWorld) || !Files.exists(legacyWorld))
            ? modernWorld
            : legacyWorld;
        if (!world.startsWith(root)) {
            throw new IOException("world directory escapes container: " + worldName);
        }
        return world;
    }

    private Path modernDimensionDirectory(Path root, String worldName) throws IOException {
        Path primaryWorld = primaryWorldDirectory(root);
        if (primaryWorld == null) {
            return null;
        }
        String namespace = "minecraft";
        String key = worldName;
        int separator = worldName.indexOf(':');
        if (separator >= 0) {
            namespace = worldName.substring(0, separator);
            key = worldName.substring(separator + 1);
        }
        if (namespace.isBlank() || key.isBlank()) {
            throw new IOException("invalid world name: " + worldName);
        }
        return primaryWorld.resolve("dimensions").resolve(namespace).resolve(key).normalize();
    }

    private Path primaryWorldDirectory(Path root) throws IOException {
        Path conventional = root.resolve("world");
        if (Files.isDirectory(conventional.resolve("dimensions"))) {
            return conventional;
        }
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> children = Files.list(root)) {
            return children
                .filter(child -> Files.isDirectory(child.resolve("dimensions")))
                .sorted()
                .findFirst()
                .orElse(null);
        }
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

    private void replaceRegionFiles(CellPlacementPlan plan, Path world, boolean clear) throws IOException {
        int minRegionX = Math.floorDiv(plan.minChunkX(), 32);
        int maxRegionX = Math.floorDiv(plan.maxChunkX(), 32);
        int minRegionZ = Math.floorDiv(plan.minChunkZ(), 32);
        int maxRegionZ = Math.floorDiv(plan.maxChunkZ(), 32);
        Path normalizedWorld = world.toAbsolutePath().normalize();
        Files.createDirectories(normalizedWorld);
        Path transactionRoot = normalizedWorld.resolve(".cloudislands-cell-place-" + UUID.randomUUID());
        Path stagedRoot = transactionRoot.resolve("staged");
        Path backup = transactionRoot.resolve("backup");
        Map<Path, Path> changed = new HashMap<>();
        Files.createDirectories(stagedRoot);
        Files.createDirectories(backup);
        try {
            List<DataSet> dataSets = List.of(
                new DataSet("region", plan.chunksDirectory(), normalizedWorld.resolve("region"), AnvilRegionRelocator.DataKind.CHUNKS),
                new DataSet("entities", plan.entitiesDirectory(), normalizedWorld.resolve("entities"), AnvilRegionRelocator.DataKind.ENTITIES),
                new DataSet("poi", plan.poiDirectory(), normalizedWorld.resolve("poi"), AnvilRegionRelocator.DataKind.POI)
            );
            for (DataSet dataSet : dataSets) {
                if (clear) {
                    Files.createDirectories(stagedRoot.resolve(dataSet.name));
                } else {
                    prepareDataSet(plan, dataSet, stagedRoot.resolve(dataSet.name), minRegionX, maxRegionX, minRegionZ, maxRegionZ);
                }
            }
            for (DataSet dataSet : dataSets) {
                publishDataSet(dataSet, stagedRoot.resolve(dataSet.name), backup.resolve(dataSet.name), changed,
                    minRegionX, maxRegionX, minRegionZ, maxRegionZ);
            }
        } catch (IOException | RuntimeException exception) {
            rollbackReplacement(changed, exception);
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

    private void prepareDataSet(CellPlacementPlan plan, DataSet dataSet, Path staged,
                                int minRegionX, int maxRegionX, int minRegionZ, int maxRegionZ) throws IOException {
        Files.createDirectories(staged);
        Path source = dataSet.source;
        if (plan.sourceOriginKnown() && (plan.sourceOriginX() != plan.originX() || plan.sourceOriginZ() != plan.originZ())) {
            new AnvilRegionRelocator().relocate(source, staged, plan.originX() - plan.sourceOriginX(), plan.originZ() - plan.sourceOriginZ(), dataSet.kind);
            source = staged;
        }
        Map<RegionCoordinate, Path> sourceFiles = sourceRegionFiles(source, minRegionX, maxRegionX, minRegionZ, maxRegionZ);
        for (Map.Entry<RegionCoordinate, Path> entry : sourceFiles.entrySet()) {
            Path destination = staged.resolve(entry.getKey().fileName());
            if (!entry.getValue().equals(destination)) {
                Files.copy(entry.getValue(), destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void publishDataSet(DataSet dataSet, Path staged, Path backup, Map<Path, Path> changed,
                                int minRegionX, int maxRegionX, int minRegionZ, int maxRegionZ) throws IOException {
        Files.createDirectories(dataSet.target);
        Files.createDirectories(backup);
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                RegionCoordinate coordinate = new RegionCoordinate(regionX, regionZ);
                Path destination = dataSet.target.resolve(coordinate.fileName());
                Path previous = backup.resolve(coordinate.fileName());
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
                        throw new IOException("invalid destination region file: " + destination);
                    }
                    Files.move(destination, previous, StandardCopyOption.ATOMIC_MOVE);
                    changed.put(destination, previous);
                }
                Path replacement = staged.resolve(coordinate.fileName());
                if (Files.isRegularFile(replacement, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(replacement, destination, StandardCopyOption.ATOMIC_MOVE);
                    changed.putIfAbsent(destination, previous);
                }
            }
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

    private void rollbackReplacement(Map<Path, Path> changed, Exception original) {
        for (Map.Entry<Path, Path> entry : changed.entrySet()) {
            Path destination = entry.getKey();
            Path previous = entry.getValue();
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

    private record DataSet(String name, Path source, Path target, AnvilRegionRelocator.DataKind kind) {}

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
