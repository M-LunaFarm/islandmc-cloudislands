package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.storage.SatisStatePortabilityPolicy;

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
            return new RelocationResult(false, false, false, false, "0,0,0", "0,0,0", "0,0,0", SatisStatePortabilityPolicy.DEFERRED_REMAP_POLICY);
        }
        int deltaX = island.hasActiveCenter() ? activeCenterX - island.activeCenterX() : 0;
        int deltaY = island.hasActiveCenter() ? activeCenterY - island.activeCenterY() : 0;
        int deltaZ = island.hasActiveCenter() ? activeCenterZ - island.activeCenterZ() : 0;
        String movementDelta = deltaX + "," + deltaY + "," + deltaZ;
        int machineDeltaX = island.hasPendingMachineRemap() ? activeCenterX - island.pendingMachineRemapCenterX() : deltaX;
        int machineDeltaY = island.hasPendingMachineRemap() ? activeCenterY - island.pendingMachineRemapCenterY() : deltaY;
        int machineDeltaZ = island.hasPendingMachineRemap() ? activeCenterZ - island.pendingMachineRemapCenterZ() : deltaZ;
        int nodeDeltaX = island.hasPendingResourceNodeRemap() ? activeCenterX - island.pendingResourceNodeRemapCenterX() : deltaX;
        int nodeDeltaY = island.hasPendingResourceNodeRemap() ? activeCenterY - island.pendingResourceNodeRemapCenterY() : deltaY;
        int nodeDeltaZ = island.hasPendingResourceNodeRemap() ? activeCenterZ - island.pendingResourceNodeRemapCenterZ() : deltaZ;
        boolean placementChanged = placementChanged(island, activeWorld, deltaX, deltaY, deltaZ);
        boolean machinesRemapped = false;
        boolean resourceNodesRemapped = false;
        boolean machineRemapDeferred = false;
        boolean resourceNodeRemapDeferred = false;
        if (machinesEnabled && machines != null) {
            machinesRemapped = machines.remapIslandRegion(islandId, activeWorld, machineDeltaX, machineDeltaY, machineDeltaZ);
            island.clearPendingMachineRemap();
        } else if (!machinesEnabled && placementChanged && island.hasActiveCenter() && !island.hasPendingMachineRemap()) {
            island.pendingMachineRemap(island.activeWorld(), island.activeCenterX(), island.activeCenterY(), island.activeCenterZ());
            machineRemapDeferred = true;
        } else if (island.hasPendingMachineRemap()) {
            machineRemapDeferred = true;
        }
        if (resourceNodesEnabled && resourceNodes != null) {
            resourceNodesRemapped = resourceNodes.remapIslandRegion(islandId, activeWorld, nodeDeltaX, nodeDeltaY, nodeDeltaZ);
            island.clearPendingResourceNodeRemap();
        } else if (!resourceNodesEnabled && placementChanged && island.hasActiveCenter() && !island.hasPendingResourceNodeRemap()) {
            island.pendingResourceNodeRemap(island.activeWorld(), island.activeCenterX(), island.activeCenterY(), island.activeCenterZ());
            resourceNodeRemapDeferred = true;
        } else if (island.hasPendingResourceNodeRemap()) {
            resourceNodeRemapDeferred = true;
        }
        island.activeWorld(activeWorld);
        island.activeCenterX(activeCenterX);
        island.activeCenterY(activeCenterY);
        island.activeCenterZ(activeCenterZ);
        return new RelocationResult(
                machinesRemapped,
                resourceNodesRemapped,
                machineRemapDeferred,
                resourceNodeRemapDeferred,
                movementDelta,
                machineDeltaX + "," + machineDeltaY + "," + machineDeltaZ,
                nodeDeltaX + "," + nodeDeltaY + "," + nodeDeltaZ,
                SatisStatePortabilityPolicy.DEFERRED_REMAP_POLICY
        );
    }

    private boolean placementChanged(FactoryIsland island, String activeWorld, int deltaX, int deltaY, int deltaZ) {
        return !activeWorld.equals(island.activeWorld())
                || deltaX != 0
                || deltaY != 0
                || deltaZ != 0;
    }

    public record RelocationResult(boolean machinesRemapped, boolean resourceNodesRemapped, boolean machineRemapDeferred, boolean resourceNodeRemapDeferred, String delta, String machineDelta, String resourceNodeDelta, String deferredRemapPolicy) {
    }
}
