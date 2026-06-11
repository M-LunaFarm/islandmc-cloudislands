package kr.lunaf.cloudislands.paper.world.cell;

import java.nio.file.Path;
import java.util.UUID;

public record CellPlacementPlan(
    UUID islandId,
    String worldName,
    int originX,
    int originZ,
    Path chunksDirectory,
    int minChunkX,
    int maxChunkX,
    int minChunkZ,
    int maxChunkZ
) {}
