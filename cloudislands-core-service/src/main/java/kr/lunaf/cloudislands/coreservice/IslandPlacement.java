package kr.lunaf.cloudislands.coreservice;

import java.util.UUID;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;

public final class IslandPlacement {
    public static final int SHARD_COUNT = 16;
    public static final int CELLS_PER_AXIS = 1024;
    private static final int MAX_PROBES = 4096;

    private IslandPlacement() {
    }

    public record Assignment(String worldName, int cellX, int cellZ) {
    }

    public static Assignment choose(UUID islandId, IslandRuntimeRepository runtimes) {
        String worldName = worldName(islandId);
        int startX = cellX(islandId);
        int startZ = cellZ(islandId);
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            int cellX = Math.floorMod(startX + probe, CELLS_PER_AXIS);
            int cellZ = Math.floorMod(startZ + (probe / CELLS_PER_AXIS), CELLS_PER_AXIS);
            if (runtimes == null || !runtimes.placementOccupied(worldName, cellX, cellZ, islandId)) {
                return new Assignment(worldName, cellX, cellZ);
            }
        }
        return new Assignment(worldName, startX, startZ);
    }

    public static String worldName(UUID islandId) {
        return "ci_shard_" + String.format("%03d", shardIndex(islandId) + 1);
    }

    public static int cellX(UUID islandId) {
        long value = islandId == null ? 0L : islandId.getMostSignificantBits() ^ (islandId.getLeastSignificantBits() << 17);
        return Math.floorMod(value, CELLS_PER_AXIS);
    }

    public static int cellZ(UUID islandId) {
        long value = islandId == null ? 0L : (islandId.getLeastSignificantBits() >>> 11) ^ Long.rotateLeft(islandId.getMostSignificantBits(), 23);
        return Math.floorMod(value, CELLS_PER_AXIS);
    }

    private static int shardIndex(UUID islandId) {
        long value = islandId == null ? 0L : islandId.getMostSignificantBits() ^ islandId.getLeastSignificantBits();
        return Math.floorMod(value, SHARD_COUNT);
    }
}
