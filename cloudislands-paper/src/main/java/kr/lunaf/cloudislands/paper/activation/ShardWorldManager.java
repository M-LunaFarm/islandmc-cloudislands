package kr.lunaf.cloudislands.paper.activation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.paper.ShardCellAllocator;

public final class ShardWorldManager {
    private static final int CELLS_PER_AXIS = 1024;
    private static final int CELLS_PER_SHARD = CELLS_PER_AXIS * CELLS_PER_AXIS;
    private final String shardWorldPrefix;
    private final int shardCount;
    private final ShardCellAllocator cellAllocator;
    private final Map<UUID, CellAssignment> activeCells = new ConcurrentHashMap<>();

    public ShardWorldManager(String shardWorldPrefix, int shardCount, int cellSize) {
        this.shardWorldPrefix = shardWorldPrefix;
        this.shardCount = shardCount;
        this.cellAllocator = new ShardCellAllocator(cellSize);
    }

    public synchronized CellAssignment allocateCell(UUID islandId) {
        CellAssignment existing = activeCells.get(islandId);
        if (existing != null) {
            return existing;
        }
        int shards = Math.max(1, shardCount);
        long totalSlots = (long) shards * CELLS_PER_SHARD;
        long baseSlot = Math.floorMod(stableSlotSeed(islandId), totalSlots);
        for (long attempt = 0L; attempt < totalSlots; attempt++) {
            long slot = (baseSlot + attempt) % totalSlots;
            int shard = (int) (slot % shards) + 1;
            int cellIndex = (int) (slot / shards);
            ShardCellAllocator.Cell cell = cellAllocator.cellForIndex(cellIndex);
            CellAssignment candidate = new CellAssignment(String.format("%s%03d", shardWorldPrefix, shard), cell.cellX(), cell.cellZ(), cell.originX(), cell.originZ());
            if (!cellOccupied(candidate)) {
                activeCells.put(islandId, candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("No free shard cells are available");
    }

    public void release(UUID islandId) {
        activeCells.remove(islandId);
    }

    private boolean cellOccupied(CellAssignment candidate) {
        String candidateKey = cellKey(candidate);
        return activeCells.values().stream().anyMatch(cell -> cellKey(cell).equals(candidateKey));
    }

    private String cellKey(CellAssignment cell) {
        return cell.worldName() + ":" + cell.cellX() + ":" + cell.cellZ();
    }

    private long stableSlotSeed(UUID islandId) {
        if (islandId == null) {
            return 0L;
        }
        return islandId.getMostSignificantBits() ^ Long.rotateLeft(islandId.getLeastSignificantBits(), 21);
    }

    public record CellAssignment(String worldName, int cellX, int cellZ, int originX, int originZ) {}
}
