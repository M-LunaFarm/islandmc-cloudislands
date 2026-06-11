package kr.lunaf.cloudislands.paper.activation;

import java.util.UUID;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class IslandActivationJobHandler {
    private final IslandStorage storage;
    private final ShardWorldManager shardWorldManager;
    private final ProtectionController protectionController;

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager, ProtectionController protectionController) {
        this.storage = storage;
        this.shardWorldManager = shardWorldManager;
        this.protectionController = protectionController;
    }

    public ActivationResult handle(IslandJob job) {
        if (job.type() != IslandJobType.ACTIVATE_ISLAND && job.type() != IslandJobType.CREATE_ISLAND) {
            return new ActivationResult(false, "UNSUPPORTED_JOB", null, null, 0, 0, 0L);
        }
        UUID islandId = job.islandId();
        try {
            IslandBundleManifest manifest = storage.readManifest(islandId);
            ShardWorldManager.CellAssignment cell = shardWorldManager.allocateCell(islandId);
            protectionController.registerIsland(islandId, cell.worldName(), cell.originX(), cell.originZ(), manifest.size(), cell.cellX(), cell.cellZ());
            return new ActivationResult(true, "ACTIVE", islandId, cell.worldName(), cell.cellX(), cell.cellZ(), manifest.schemaVersion());
        } catch (Exception exception) {
            return new ActivationResult(false, "ERROR_ACTIVATING", islandId, null, 0, 0, 0L);
        }
    }

    public record ActivationResult(boolean success, String state, UUID islandId, String worldName, int cellX, int cellZ, long schemaVersion) {}
}
