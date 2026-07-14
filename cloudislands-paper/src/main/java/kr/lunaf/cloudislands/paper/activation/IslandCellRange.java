package kr.lunaf.cloudislands.paper.activation;

import java.util.UUID;
import kr.lunaf.cloudislands.paper.world.cell.CellPlacementPlan;

public record IslandCellRange(UUID islandId, String worldName, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
    public static IslandCellRange from(CellPlacementPlan plan) {
        return new IslandCellRange(plan.islandId(), plan.worldName(), plan.minChunkX(), plan.maxChunkX(), plan.minChunkZ(), plan.maxChunkZ());
    }

    public static IslandCellRange from(ActiveIslandRegistry.ActiveIsland island) {
        int half = Math.max(1, island.islandSize() / 2);
        return new IslandCellRange(
            island.islandId(),
            island.worldName(),
            Math.floorDiv(island.originX() - half, 16),
            Math.floorDiv(island.originX() + half, 16),
            Math.floorDiv(island.originZ() - half, 16),
            Math.floorDiv(island.originZ() + half, 16)
        );
    }
}
