package kr.lunaf.cloudislands.paper.activation;

import java.util.UUID;
import kr.lunaf.cloudislands.paper.ProtectionController;

public final class IslandDeactivationHandler {
    private final ActiveIslandRegistry activeIslands;
    private final ShardWorldManager shardWorldManager;
    private final ProtectionController protectionController;

    public IslandDeactivationHandler(ActiveIslandRegistry activeIslands, ShardWorldManager shardWorldManager, ProtectionController protectionController) {
        this.activeIslands = activeIslands;
        this.shardWorldManager = shardWorldManager;
        this.protectionController = protectionController;
    }

    public void deactivate(UUID islandId) {
        protectionController.unregisterIsland(islandId);
        activeIslands.deactivated(islandId);
        shardWorldManager.release(islandId);
    }
}
