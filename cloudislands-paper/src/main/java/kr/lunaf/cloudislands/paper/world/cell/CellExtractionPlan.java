package kr.lunaf.cloudislands.paper.world.cell;

import java.nio.file.Path;
import java.util.UUID;

public record CellExtractionPlan(
    UUID islandId,
    String worldName,
    int originX,
    int originZ,
    Path targetChunksDirectory,
    int minChunkX,
    int maxChunkX,
    int minChunkZ,
    int maxChunkZ
) {}
