package kr.lunaf.cloudislands.paper.world.cell;

import java.nio.file.Path;
import java.util.UUID;

public record CellExtractionPlan(
    UUID islandId,
    String worldName,
    int originX,
    int originZ,
    Path targetChunksDirectory,
    Path targetEntitiesDirectory,
    Path targetPoiDirectory,
    int minChunkX,
    int maxChunkX,
    int minChunkZ,
    int maxChunkZ
) {
    public CellExtractionPlan(UUID islandId, String worldName, int originX, int originZ, Path targetChunksDirectory,
                              int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        this(islandId, worldName, originX, originZ, targetChunksDirectory,
            targetChunksDirectory.resolveSibling("entities"), targetChunksDirectory.resolveSibling("poi"),
            minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }
}
