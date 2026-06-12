package kr.lunaf.cloudislands.migration.world;

import java.nio.file.Path;
import java.util.UUID;

public record MigrationWorldBundle(UUID islandId, Path sourcePath, Path bundlePath, String checksum, long sizeBytes, long fileCount) {}
