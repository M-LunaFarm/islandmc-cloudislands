package kr.lunaf.cloudislands.coreservice;

import java.util.UUID;

public final class IslandPlacement {
    private static final int SHARD_COUNT = 16;
    private static final int CELLS_PER_AXIS = 1024;

    private IslandPlacement() {
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
