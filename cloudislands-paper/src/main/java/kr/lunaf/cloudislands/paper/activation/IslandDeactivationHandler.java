package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.ProtectionController;

public final class IslandDeactivationHandler {
    private final ActiveIslandRegistry activeIslands;
    private final ShardWorldManager shardWorldManager;
    private final ProtectionController protectionController;
    private final IslandSaveService saveService;

    public IslandDeactivationHandler(ActiveIslandRegistry activeIslands, ShardWorldManager shardWorldManager, ProtectionController protectionController) {
        this(activeIslands, shardWorldManager, protectionController, null);
    }

    public IslandDeactivationHandler(ActiveIslandRegistry activeIslands, ShardWorldManager shardWorldManager, ProtectionController protectionController, IslandSaveService saveService) {
        this.activeIslands = activeIslands;
        this.shardWorldManager = shardWorldManager;
        this.protectionController = protectionController;
        this.saveService = saveService;
    }

    public DeactivationResult deactivate(UUID islandId) {
        return deactivate(islandId, false);
    }

    public DeactivationResult deactivate(UUID islandId, boolean deleteBackup) {
        try {
            IslandSaveService.SaveResult saveResult = null;
            ActiveIslandRegistry.ActiveIsland active = activeIslands.find(islandId).orElse(null);
            if (active != null && saveService != null) {
                saveResult = deleteBackup ? saveService.backupBeforeDelete(islandId, active) : saveService.save(islandId, active);
            }
            protectionController.unregisterIsland(islandId);
            activeIslands.deactivated(islandId);
            shardWorldManager.release(islandId);
            return new DeactivationResult(true, islandId, saveResult == null ? 0L : saveResult.snapshotNo(), saveResult == null ? "" : saveResult.checksum(), saveResult == null ? 0L : saveResult.sizeBytes(), null);
        } catch (IOException exception) {
            return new DeactivationResult(false, islandId, 0L, "", 0L, exception.getMessage());
        }
    }

    public DeactivationResult saveOnly(UUID islandId) {
        try {
            IslandSaveService.SaveResult saveResult = null;
            ActiveIslandRegistry.ActiveIsland active = activeIslands.find(islandId).orElse(null);
            if (active == null) {
                return new DeactivationResult(false, islandId, 0L, "", 0L, "ISLAND_NOT_ACTIVE");
            }
            if (saveService == null) {
                return new DeactivationResult(false, islandId, 0L, "", 0L, "SAVE_UNAVAILABLE");
            }
            saveResult = saveService.save(islandId, active);
            return new DeactivationResult(true, islandId, saveResult == null ? 0L : saveResult.snapshotNo(), saveResult == null ? "" : saveResult.checksum(), saveResult == null ? 0L : saveResult.sizeBytes(), null);
        } catch (IOException exception) {
            return new DeactivationResult(false, islandId, 0L, "", 0L, exception.getMessage());
        }
    }

    public record DeactivationResult(boolean success, UUID islandId, long snapshotNo, String checksum, long sizeBytes, String errorMessage) {}
}
