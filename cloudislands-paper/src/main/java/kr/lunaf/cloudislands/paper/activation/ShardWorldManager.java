package kr.lunaf.cloudislands.paper.activation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.paper.ShardCellAllocator;

public final class ShardWorldManager {
    private final String shardWorldPrefix;
    private final int shardCount;
    private final ShardCellAllocator cellAllocator;
    private final Map<UUID, CellAssignment> activeCells = new ConcurrentHashMap<>();

    public ShardWorldManager(String shardWorldPrefix, int shardCount, int cellSize) {
        this.shardWorldPrefix = shardWorldPrefix;
        this.shardCount = shardCount;
        this.cellAllocator = new ShardCellAllocator(cellSize);
    }

    public CellAssignment allocateCell(UUID islandId) {
        return activeCells.computeIfAbsent(islandId, id -> {
            int slot = Math.floorMod(id.hashCode(), Math.max(1, shardCount * 1024));
            int shard = Math.floorMod(slot, Math.max(1, shardCount)) + 1;
            ShardCellAllocator.Cell cell = cellAllocator.cellForIndex(slot / Math.max(1, shardCount));
            return new CellAssignment(String.format("%s%03d", shardWorldPrefix, shard), cell.cellX(), cell.cellZ(), cell.originX(), cell.originZ());
        });
    }

    public void release(UUID islandId) {
        activeCells.remove(islandId);
    }

    public record CellAssignment(String worldName, int cellX, int cellZ, int originX, int originZ) {}
}
