package kr.lunaf.cloudislands.paper.world.cell;

import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlan;

public final class ShardCellTransferPlanner {
    private final int islandSize;

    public ShardCellTransferPlanner(int islandSize) {
        this.islandSize = islandSize;
    }

    public CellPlacementPlan placement(BundleRestorePlan restorePlan) {
        Bounds bounds = bounds(restorePlan.originX(), restorePlan.originZ());
        return new CellPlacementPlan(restorePlan.islandId(), restorePlan.worldName(), restorePlan.originX(), restorePlan.originZ(), restorePlan.chunksDirectory(), bounds.minChunkX(), bounds.maxChunkX(), bounds.minChunkZ(), bounds.maxChunkZ());
    }

    public CellExtractionPlan extraction(UUID islandId, String worldName, int originX, int originZ, Path targetChunksDirectory) {
        Bounds bounds = bounds(originX, originZ);
        return new CellExtractionPlan(islandId, worldName, originX, originZ, targetChunksDirectory, bounds.minChunkX(), bounds.maxChunkX(), bounds.minChunkZ(), bounds.maxChunkZ());
    }

    private Bounds bounds(int originX, int originZ) {
        int half = Math.max(1, islandSize / 2);
        int minChunkX = Math.floorDiv(originX - half, 16);
        int maxChunkX = Math.floorDiv(originX + half, 16);
        int minChunkZ = Math.floorDiv(originZ - half, 16);
        int maxChunkZ = Math.floorDiv(originZ + half, 16);
        return new Bounds(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    private record Bounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {}
}
