package kr.lunaf.cloudislands.migration.world;

import java.nio.file.Path;
import java.util.UUID;

public record MigrationWorldBundle(UUID islandId, Path sourcePath, Path bundlePath, Path manifestPath, String checksum, long sizeBytes, long fileCount, String compression, boolean runtimeRestoreCompatible) {
    public MigrationWorldBundle(UUID islandId, Path sourcePath, Path bundlePath, Path manifestPath, String checksum, long sizeBytes, long fileCount) {
        this(islandId, sourcePath, bundlePath, manifestPath, checksum, sizeBytes, fileCount, "zip", false);
    }

    public MigrationWorldBundle(UUID islandId, Path sourcePath, Path bundlePath, String checksum, long sizeBytes, long fileCount) {
        this(islandId, sourcePath, bundlePath, bundlePath == null ? null : bundlePath.resolveSibling("manifest.json"), checksum, sizeBytes, fileCount);
    }

    public String restoreCompatibilityStatus() {
        return runtimeRestoreCompatible ? "runtime-restore-ready" : "migration-import-bundle-conversion-required";
    }
}
