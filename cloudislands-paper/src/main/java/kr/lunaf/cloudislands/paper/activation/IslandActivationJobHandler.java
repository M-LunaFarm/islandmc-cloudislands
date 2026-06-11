package kr.lunaf.cloudislands.paper.activation;

import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class IslandActivationJobHandler {
    private final IslandStorage storage;
    private final ShardWorldManager shardWorldManager;

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager) {
        this.storage = storage;
        this.shardWorldManager = shardWorldManager;
    }

    public ActivationResult handle(IslandJob job) {
        if (job.type() != IslandJobType.ACTIVATE_ISLAND && job.type() != IslandJobType.CREATE_ISLAND) {
            return new ActivationResult(false, "UNSUPPORTED_JOB", null, null, 0, 0);
        }
        UUID islandId = job.islandId();
        try {
            storage.readManifest(islandId);
            ShardWorldManager.CellAssignment cell = shardWorldManager.allocateCell(islandId);
            return new ActivationResult(true, "ACTIVE", islandId, cell.worldName(), cell.cellX(), cell.cellZ());
        } catch (Exception exception) {
            return new ActivationResult(false, "ERROR_ACTIVATING", islandId, null, 0, 0);
        }
    }

    public record ActivationResult(boolean success, String state, UUID islandId, String worldName, int cellX, int cellZ) {}
}
