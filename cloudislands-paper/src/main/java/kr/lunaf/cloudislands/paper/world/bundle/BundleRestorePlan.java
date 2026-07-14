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
    Path chunksDirectory,
    Path entitiesDirectory,
    Path poiDirectory,
    int sourceOriginX,
    int sourceOriginZ,
    boolean sourceOriginKnown
) {
    public BundleRestorePlan(UUID islandId, String worldName, int originX, int originZ, Path stagedBundle, Path extractedRoot, Path chunksDirectory) {
        this(islandId, worldName, originX, originZ, stagedBundle, extractedRoot, chunksDirectory, extractedRoot.resolve("entities"), extractedRoot.resolve("poi"), 0, 0, false);
    }

    public BundleRestorePlan withSourceOrigin(int sourceOriginX, int sourceOriginZ, boolean sourceOriginKnown) {
        return new BundleRestorePlan(islandId, worldName, originX, originZ, stagedBundle, extractedRoot, chunksDirectory, entitiesDirectory, poiDirectory, sourceOriginX, sourceOriginZ, sourceOriginKnown);
    }
}
