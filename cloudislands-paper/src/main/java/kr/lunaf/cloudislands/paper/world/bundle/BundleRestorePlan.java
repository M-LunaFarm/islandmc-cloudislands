package kr.lunaf.cloudislands.paper.world.bundle;

import java.nio.file.Path;
import java.util.UUID;

public record BundleRestorePlan(
    UUID islandId,
    String worldName,
    int originX,
    int originZ,
    Path stagedBundle,
    Path extractedRoot,
    Path chunksDirectory
) {}
