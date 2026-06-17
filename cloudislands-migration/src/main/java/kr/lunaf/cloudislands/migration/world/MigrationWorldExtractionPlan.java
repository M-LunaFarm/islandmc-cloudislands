package kr.lunaf.cloudislands.migration.world;

import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.migration.MigrationManifest;

public record MigrationWorldExtractionPlan(UUID islandId, Path sourcePath, Path targetBundlePath, MigrationManifest manifest) {
    public MigrationWorldExtractionPlan(UUID islandId, Path sourcePath, Path targetBundlePath) {
        this(islandId, sourcePath, targetBundlePath, null);
    }
}
