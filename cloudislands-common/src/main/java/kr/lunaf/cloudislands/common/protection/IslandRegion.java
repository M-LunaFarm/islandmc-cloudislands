package kr.lunaf.cloudislands.common.protection;

import kr.lunaf.cloudislands.common.island.PortableIslandCoordinateMapper;

import java.util.UUID;

public record IslandRegion(UUID islandId, String world, int minX, int maxX, int minZ, int maxZ, int cellX, int cellZ) {
    public static IslandRegion fromRuntimePlacement(
            UUID islandId,
            PortableIslandCoordinateMapper.RuntimePlacement placement,
            int islandHalfSize
    ) {
        if (islandHalfSize < 0) {
            throw new IllegalArgumentException("islandHalfSize must not be negative");
        }
        int originX = placement.originBlockX();
        int originZ = placement.originBlockZ();
        return new IslandRegion(
                islandId,
                placement.worldName(),
                originX - islandHalfSize,
                originX + islandHalfSize,
                originZ - islandHalfSize,
                originZ + islandHalfSize,
                placement.cellX(),
                placement.cellZ()
        );
    }

    public boolean contains(String otherWorld, int blockX, int blockZ) {
        return world.equals(otherWorld) && blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }

    public double originX() {
        return (minX + maxX) / 2.0D;
    }

    public double originZ() {
        return (minZ + maxZ) / 2.0D;
    }
}
