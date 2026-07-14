package kr.lunaf.cloudislands.paper.world.cell;

import java.nio.file.Path;
import java.util.UUID;

public record CellPlacementPlan(
    UUID islandId,
    String worldName,
    int originX,
    int originZ,
    Path chunksDirectory,
    Path entitiesDirectory,
    Path poiDirectory,
    int minChunkX,
    int maxChunkX,
    int minChunkZ,
    int maxChunkZ,
    int sourceOriginX,
    int sourceOriginZ,
    boolean sourceOriginKnown
) {
    public CellPlacementPlan(UUID islandId, String worldName, int originX, int originZ, Path chunksDirectory,
                             int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        this(islandId, worldName, originX, originZ, chunksDirectory, chunksDirectory.resolveSibling("entities"), chunksDirectory.resolveSibling("poi"), minChunkX, maxChunkX, minChunkZ, maxChunkZ, 0, 0, false);
    }

    public CellPlacementPlan(UUID islandId, String worldName, int originX, int originZ, Path chunksDirectory,
                             int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                             int sourceOriginX, int sourceOriginZ, boolean sourceOriginKnown) {
        this(islandId, worldName, originX, originZ, chunksDirectory, chunksDirectory.resolveSibling("entities"), chunksDirectory.resolveSibling("poi"),
            minChunkX, maxChunkX, minChunkZ, maxChunkZ, sourceOriginX, sourceOriginZ, sourceOriginKnown);
    }
}
