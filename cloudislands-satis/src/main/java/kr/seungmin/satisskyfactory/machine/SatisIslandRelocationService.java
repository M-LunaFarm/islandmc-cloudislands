package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.storage.SatisStatePortabilityPolicy;

import java.util.Collection;
import java.util.UUID;

public final class SatisIslandRelocationService {
    private final MachineService machines;
    private final ResourceNodeService resourceNodes;

    public SatisIslandRelocationService(MachineService machines, ResourceNodeService resourceNodes) {
        this.machines = machines;
        this.resourceNodes = resourceNodes;
    }

    public RelocationCheckpoint checkpoint(FactoryIsland island) {
        return new RelocationCheckpoint(
                island.activeWorld(), island.activeCenterX(), island.activeCenterY(), island.activeCenterZ(),
                island.pendingMachineRemapWorld(), island.pendingMachineRemapCenterX(),
                island.pendingMachineRemapCenterY(), island.pendingMachineRemapCenterZ(),
                island.pendingResourceNodeRemapWorld(), island.pendingResourceNodeRemapCenterX(),
                island.pendingResourceNodeRemapCenterY(), island.pendingResourceNodeRemapCenterZ()
        );
    }

    public boolean rollback(UUID islandId, FactoryIsland island, RelocationResult result, RelocationCheckpoint checkpoint) {
        if (islandId == null || island == null || result == null || checkpoint == null) {
            return false;
        }
        int machineDeltaX;
        int machineDeltaY;
        int machineDeltaZ;
        int nodeDeltaX;
        int nodeDeltaY;
        int nodeDeltaZ;
        try {
            machineDeltaX = Math.subtractExact(checkpoint.machineOriginX(), island.activeCenterX());
            machineDeltaY = Math.subtractExact(checkpoint.machineOriginY(), island.activeCenterY());
            machineDeltaZ = Math.subtractExact(checkpoint.machineOriginZ(), island.activeCenterZ());
            nodeDeltaX = Math.subtractExact(checkpoint.resourceNodeOriginX(), island.activeCenterX());
            nodeDeltaY = Math.subtractExact(checkpoint.resourceNodeOriginY(), island.activeCenterY());
            nodeDeltaZ = Math.subtractExact(checkpoint.resourceNodeOriginZ(), island.activeCenterZ());
        } catch (ArithmeticException overflow) {
            return false;
        }
        boolean machinesRolledBack = !result.machinesRemapped() || machines == null
                || machines.remapIslandRegion(islandId, checkpoint.machineOriginWorld(), machineDeltaX, machineDeltaY, machineDeltaZ);
        boolean nodesRolledBack = !result.resourceNodesRemapped() || resourceNodes == null
                || resourceNodes.remapIslandRegion(islandId, checkpoint.resourceNodeOriginWorld(), nodeDeltaX, nodeDeltaY, nodeDeltaZ);
        if (!machinesRolledBack || !nodesRolledBack) {
            return false;
        }
        checkpoint.restore(island);
        return true;
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
            return new RelocationResult(false, false, false, false, false, "", "0,0,0", "", "0,0,0", "0,0,0", "0,0,0", "0,0,0", SatisStatePortabilityPolicy.DEFERRED_REMAP_POLICY);
        }
        String previousWorld = island.activeWorld();
        String previousCenter = island.hasActiveCenter()
                ? island.activeCenterX() + "," + island.activeCenterY() + "," + island.activeCenterZ()
                : "unplaced";
        String targetCenter = activeCenterX + "," + activeCenterY + "," + activeCenterZ;
        int deltaX;
        int deltaY;
        int deltaZ;
        int machineDeltaX;
        int machineDeltaY;
        int machineDeltaZ;
        int nodeDeltaX;
        int nodeDeltaY;
        int nodeDeltaZ;
        try {
            deltaX = island.hasActiveCenter() ? Math.subtractExact(activeCenterX, island.activeCenterX()) : 0;
            deltaY = island.hasActiveCenter() ? Math.subtractExact(activeCenterY, island.activeCenterY()) : 0;
            deltaZ = island.hasActiveCenter() ? Math.subtractExact(activeCenterZ, island.activeCenterZ()) : 0;
            machineDeltaX = island.hasPendingMachineRemap() ? Math.subtractExact(activeCenterX, island.pendingMachineRemapCenterX()) : deltaX;
            machineDeltaY = island.hasPendingMachineRemap() ? Math.subtractExact(activeCenterY, island.pendingMachineRemapCenterY()) : deltaY;
            machineDeltaZ = island.hasPendingMachineRemap() ? Math.subtractExact(activeCenterZ, island.pendingMachineRemapCenterZ()) : deltaZ;
            nodeDeltaX = island.hasPendingResourceNodeRemap() ? Math.subtractExact(activeCenterX, island.pendingResourceNodeRemapCenterX()) : deltaX;
            nodeDeltaY = island.hasPendingResourceNodeRemap() ? Math.subtractExact(activeCenterY, island.pendingResourceNodeRemapCenterY()) : deltaY;
            nodeDeltaZ = island.hasPendingResourceNodeRemap() ? Math.subtractExact(activeCenterZ, island.pendingResourceNodeRemapCenterZ()) : deltaZ;
        } catch (ArithmeticException overflow) {
            return new RelocationResult(false, false, island.hasPendingMachineRemap(), island.hasPendingResourceNodeRemap(),
                    false, previousWorld, previousCenter, activeWorld, targetCenter, "overflow", "overflow", "overflow",
                    SatisStatePortabilityPolicy.DEFERRED_REMAP_POLICY);
        }
        String movementDelta = deltaX + "," + deltaY + "," + deltaZ;
        boolean placementChanged = placementChanged(island, activeWorld, deltaX, deltaY, deltaZ);
        boolean machinesRemapped = false;
        boolean resourceNodesRemapped = false;
        boolean machineRemapDeferred = false;
        boolean resourceNodeRemapDeferred = false;
        if (machinesEnabled && machines != null) {
            boolean remapRequired = remapRequired(machines.byIsland(islandId), activeWorld,
                    machineDeltaX, machineDeltaY, machineDeltaZ, machine -> machine.location().world());
            if (remapRequired) {
                machinesRemapped = machines.remapIslandRegion(islandId, activeWorld, machineDeltaX, machineDeltaY, machineDeltaZ);
                if (machinesRemapped) {
                    island.clearPendingMachineRemap();
                } else {
                    deferMachineRemap(island);
                    machineRemapDeferred = true;
                }
            } else {
                island.clearPendingMachineRemap();
            }
        } else if (placementChanged && island.hasActiveCenter()) {
            deferMachineRemap(island);
            machineRemapDeferred = true;
        } else if (island.hasPendingMachineRemap()) {
            machineRemapDeferred = true;
        }
        if (resourceNodesEnabled && resourceNodes != null) {
            boolean remapRequired = remapRequired(resourceNodes.nodes(islandId), activeWorld,
                    nodeDeltaX, nodeDeltaY, nodeDeltaZ, node -> node.location().world());
            if (remapRequired) {
                resourceNodesRemapped = resourceNodes.remapIslandRegion(islandId, activeWorld, nodeDeltaX, nodeDeltaY, nodeDeltaZ);
                if (resourceNodesRemapped) {
                    island.clearPendingResourceNodeRemap();
                } else {
                    deferResourceNodeRemap(island);
                    resourceNodeRemapDeferred = true;
                }
            } else {
                island.clearPendingResourceNodeRemap();
            }
        } else if (placementChanged && island.hasActiveCenter()) {
            deferResourceNodeRemap(island);
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
                placementChanged,
                previousWorld,
                previousCenter,
                activeWorld,
                targetCenter,
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

    private <T> boolean remapRequired(Collection<T> values, String targetWorld, int deltaX, int deltaY, int deltaZ,
                                      java.util.function.Function<T, String> world) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return deltaX != 0 || deltaY != 0 || deltaZ != 0
                || values.stream().anyMatch(value -> !targetWorld.equals(world.apply(value)));
    }

    private void deferMachineRemap(FactoryIsland island) {
        if (!island.hasPendingMachineRemap() && island.hasActiveCenter()) {
            island.pendingMachineRemap(island.activeWorld(), island.activeCenterX(), island.activeCenterY(), island.activeCenterZ());
        }
    }

    private void deferResourceNodeRemap(FactoryIsland island) {
        if (!island.hasPendingResourceNodeRemap() && island.hasActiveCenter()) {
            island.pendingResourceNodeRemap(island.activeWorld(), island.activeCenterX(), island.activeCenterY(), island.activeCenterZ());
        }
    }

    public record RelocationResult(boolean machinesRemapped, boolean resourceNodesRemapped, boolean machineRemapDeferred, boolean resourceNodeRemapDeferred, boolean placementChanged, String previousWorld, String previousCenter, String targetWorld, String targetCenter, String delta, String machineDelta, String resourceNodeDelta, String deferredRemapPolicy) {
    }

    public record RelocationCheckpoint(
            String activeWorld, int activeCenterX, int activeCenterY, int activeCenterZ,
            String pendingMachineWorld, int pendingMachineCenterX, int pendingMachineCenterY, int pendingMachineCenterZ,
            String pendingResourceNodeWorld, int pendingResourceNodeCenterX, int pendingResourceNodeCenterY,
            int pendingResourceNodeCenterZ
    ) {
        String machineOriginWorld() {
            return pendingMachineWorld == null || pendingMachineWorld.isBlank() ? activeWorld : pendingMachineWorld;
        }

        int machineOriginX() { return pendingMachineWorld == null || pendingMachineWorld.isBlank() ? activeCenterX : pendingMachineCenterX; }
        int machineOriginY() { return pendingMachineWorld == null || pendingMachineWorld.isBlank() ? activeCenterY : pendingMachineCenterY; }
        int machineOriginZ() { return pendingMachineWorld == null || pendingMachineWorld.isBlank() ? activeCenterZ : pendingMachineCenterZ; }

        String resourceNodeOriginWorld() {
            return pendingResourceNodeWorld == null || pendingResourceNodeWorld.isBlank() ? activeWorld : pendingResourceNodeWorld;
        }

        int resourceNodeOriginX() { return pendingResourceNodeWorld == null || pendingResourceNodeWorld.isBlank() ? activeCenterX : pendingResourceNodeCenterX; }
        int resourceNodeOriginY() { return pendingResourceNodeWorld == null || pendingResourceNodeWorld.isBlank() ? activeCenterY : pendingResourceNodeCenterY; }
        int resourceNodeOriginZ() { return pendingResourceNodeWorld == null || pendingResourceNodeWorld.isBlank() ? activeCenterZ : pendingResourceNodeCenterZ; }

        void restore(FactoryIsland island) {
            island.activeWorld(activeWorld);
            island.activeCenterX(activeCenterX);
            island.activeCenterY(activeCenterY);
            island.activeCenterZ(activeCenterZ);
            island.pendingMachineRemap(pendingMachineWorld, pendingMachineCenterX, pendingMachineCenterY, pendingMachineCenterZ);
            island.pendingResourceNodeRemap(pendingResourceNodeWorld, pendingResourceNodeCenterX,
                    pendingResourceNodeCenterY, pendingResourceNodeCenterZ);
        }
    }
}
