package kr.lunaf.cloudislands.common.protection;

import java.util.UUID;

public record IslandRegion(UUID islandId, String world, int minX, int maxX, int minZ, int maxZ, int cellX, int cellZ) {
    public boolean contains(String otherWorld, int blockX, int blockZ) {
        return world.equals(otherWorld) && blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }
}
