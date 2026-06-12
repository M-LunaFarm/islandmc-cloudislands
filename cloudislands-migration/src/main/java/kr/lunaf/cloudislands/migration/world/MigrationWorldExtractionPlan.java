package kr.lunaf.cloudislands.migration.world;

import java.nio.file.Path;
import java.util.UUID;

public record MigrationWorldExtractionPlan(UUID islandId, Path sourcePath, Path targetBundlePath) {}
