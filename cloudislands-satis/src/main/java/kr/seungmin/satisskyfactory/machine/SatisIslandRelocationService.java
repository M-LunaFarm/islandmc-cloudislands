package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;

import java.util.UUID;

public final class SatisIslandRelocationService {
    private final MachineService machines;
    private final ResourceNodeService resourceNodes;

    public SatisIslandRelocationService(MachineService machines, ResourceNodeService resourceNodes) {
        this.machines = machines;
        this.resourceNodes = resourceNodes;
    }

    public RelocationResult relocate(
            UUID islandId,
            FactoryIsland island,
            String activeWorld,
            int activeCenterX,
            int activeCenterY,
            int activeCenterZ,
            boolean machinesEnabled,
            boolean resourceNodesEnabled
    ) {
        if (islandId == null || island == null || activeWorld == null || activeWorld.isBlank()) {
            return new RelocationResult(false, false, "0,0,0");
        }
        int deltaX = island.hasActiveCenter() ? activeCenterX - island.activeCenterX() : 0;
        int deltaY = island.hasActiveCenter() ? activeCenterY - island.activeCenterY() : 0;
        int deltaZ = island.hasActiveCenter() ? activeCenterZ - island.activeCenterZ() : 0;
        boolean machinesRemapped = machinesEnabled
                && machines != null
                && machines.remapIslandRegion(islandId, activeWorld, deltaX, deltaY, deltaZ);
        boolean resourceNodesRemapped = resourceNodesEnabled
                && resourceNodes != null
                && resourceNodes.remapIslandRegion(islandId, activeWorld, deltaX, deltaY, deltaZ);
        island.activeWorld(activeWorld);
        island.activeCenterX(activeCenterX);
        island.activeCenterY(activeCenterY);
        island.activeCenterZ(activeCenterZ);
        return new RelocationResult(machinesRemapped, resourceNodesRemapped, deltaX + "," + deltaY + "," + deltaZ);
    }

    public record RelocationResult(boolean machinesRemapped, boolean resourceNodesRemapped, String delta) {
    }
}
