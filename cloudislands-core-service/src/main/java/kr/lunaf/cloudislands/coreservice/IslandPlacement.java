package kr.lunaf.cloudislands.coreservice;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
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
        return choose(islandId, runtimes, 0);
    }

    public static IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, IslandRuntimeRepository runtimes) {
        RuntimeException lastError = null;
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            Assignment placement = choose(islandId, runtimes, probe);
            try {
                return runtimes.markActivating(islandId, targetNode, placement.worldName(), placement.cellX(), placement.cellZ());
            } catch (RuntimeException exception) {
                if (!placementConflict(exception)) {
                    throw exception;
                }
                lastError = exception;
            }
        }
        throw lastError == null ? new IllegalStateException("no island placement available") : lastError;
    }

    private static Assignment choose(UUID islandId, IslandRuntimeRepository runtimes, int offset) {
        String worldName = worldName(islandId);
        int startX = cellX(islandId);
        int startZ = cellZ(islandId);
        for (int probe = offset; probe < MAX_PROBES; probe++) {
            int cellX = Math.floorMod(startX + probe, CELLS_PER_AXIS);
            int cellZ = Math.floorMod(startZ + (probe / CELLS_PER_AXIS), CELLS_PER_AXIS);
            if (runtimes == null || !runtimes.placementOccupied(worldName, cellX, cellZ, islandId)) {
                return new Assignment(worldName, cellX, cellZ);
            }
        }
        return new Assignment(worldName, startX, startZ);
    }

    private static boolean placementConflict(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("idx_island_runtime_active_placement")
                    || lower.contains("duplicate key")
                    || lower.contains("unique constraint")
                    || lower.contains("unique index")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
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
