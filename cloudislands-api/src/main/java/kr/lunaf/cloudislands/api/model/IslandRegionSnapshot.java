package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandRegionSnapshot(
    UUID islandId,
    String worldName,
    int minX,
    int maxX,
    int minZ,
    int maxZ,
    int cellX,
    int cellZ,
    double originX,
    double originZ
) {}
