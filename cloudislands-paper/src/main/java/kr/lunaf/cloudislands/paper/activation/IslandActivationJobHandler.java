package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.ShardWorldPreloader;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlan;
import kr.lunaf.cloudislands.paper.world.cell.CellPlacementPlan;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.cell.ShardCellTransferPlanner;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class IslandActivationJobHandler {
    private final IslandStorage storage;
    private final ShardWorldManager shardWorldManager;
    private final ProtectionController protectionController;
    private final IslandWorldRestorer worldRestorer;
    private final ShardWorldPreloader preloader;
    private final int preloadRadius;
    private final FileBackedCellTransfer cellTransfer;
    private final ActiveIslandRegistry activeIslands;
    private final IslandSaveService saveService;

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager, ProtectionController protectionController) {
        this(storage, shardWorldManager, protectionController, null, null, 0, null);
    }

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager, ProtectionController protectionController, IslandWorldRestorer worldRestorer, ShardWorldPreloader preloader, int preloadRadius) {
        this(storage, shardWorldManager, protectionController, worldRestorer, preloader, preloadRadius, null);
    }

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager, ProtectionController protectionController, IslandWorldRestorer worldRestorer, ShardWorldPreloader preloader, int preloadRadius, FileBackedCellTransfer cellTransfer) {
        this(storage, shardWorldManager, protectionController, worldRestorer, preloader, preloadRadius, cellTransfer, null, null);
    }

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager, ProtectionController protectionController, IslandWorldRestorer worldRestorer, ShardWorldPreloader preloader, int preloadRadius, FileBackedCellTransfer cellTransfer, ActiveIslandRegistry activeIslands, IslandSaveService saveService) {
        this.storage = storage;
        this.shardWorldManager = shardWorldManager;
        this.protectionController = protectionController;
        this.worldRestorer = worldRestorer;
        this.preloader = preloader;
        this.preloadRadius = preloadRadius;
        this.cellTransfer = cellTransfer;
        this.activeIslands = activeIslands;
        this.saveService = saveService;
    }

    public ActivationResult handle(IslandJob job) {
        if (job.type() != IslandJobType.ACTIVATE_ISLAND && job.type() != IslandJobType.CREATE_ISLAND && job.type() != IslandJobType.RESTORE_ISLAND && job.type() != IslandJobType.RESET_ISLAND) {
            return new ActivationResult(false, "UNSUPPORTED_JOB", null, null, 0, 0, 0, 0, 0, 0L, 0L, null, 0L, "", 0L, "");
        }
        UUID islandId = job.islandId();
        try {
            IslandBundleManifest manifest = storage.readManifest(islandId);
            IslandSaveService.SaveResult preMutationSnapshot = snapshotBeforeMutation(job);
            ShardWorldManager.CellAssignment cell = shardWorldManager.allocateCell(islandId);
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            BundleRestorePlan restorePlan = stageBundle(islandId, cell, snapshotNo);
            if (restorePlan != null && cellTransfer != null) {
                CellPlacementPlan placement = new ShardCellTransferPlanner(manifest.size()).placement(restorePlan);
                cellTransfer.place(placement);
            }
            if (job.type() == IslandJobType.RESTORE_ISLAND && snapshotNo > 0L) {
                storage.promoteSnapshot(islandId, snapshotNo);
            }
            if (preloader != null) {
                preloader.preload(cell.worldName(), cell.originX(), cell.originZ(), preloadRadius);
            }
            protectionController.registerIsland(islandId, cell.worldName(), cell.originX(), cell.originZ(), manifest.size(), cell.cellX(), cell.cellZ());
            return new ActivationResult(true, "ACTIVE", islandId, cell.worldName(), cell.cellX(), cell.cellZ(), cell.originX(), cell.originZ(), manifest.size(), manifest.schemaVersion(), longValue(job.payload().get("fencingToken")), restorePlan == null ? null : restorePlan.extractedRoot().toString(), preMutationSnapshot == null ? 0L : preMutationSnapshot.snapshotNo(), preMutationSnapshot == null ? "" : preMutationSnapshot.checksum(), preMutationSnapshot == null ? 0L : preMutationSnapshot.sizeBytes(), preMutationReason(job));
        } catch (Exception exception) {
            return new ActivationResult(false, "ERROR_ACTIVATING", islandId, null, 0, 0, 0, 0, 0, 0L, 0L, null, 0L, "", 0L, "");
        }
    }

    private IslandSaveService.SaveResult snapshotBeforeMutation(IslandJob job) throws IOException {
        if ((job.type() != IslandJobType.RESTORE_ISLAND && job.type() != IslandJobType.RESET_ISLAND) || activeIslands == null || saveService == null) {
            return null;
        }
        ActiveIslandRegistry.ActiveIsland activeIsland = activeIslands.find(job.islandId()).orElse(null);
        if (activeIsland == null) {
            return null;
        }
        return job.type() == IslandJobType.RESET_ISLAND
            ? saveService.snapshotBeforeReset(job.islandId(), activeIsland)
            : saveService.snapshotBeforeRestore(job.islandId(), activeIsland);
    }

    private String preMutationReason(IslandJob job) {
        if (job.type() == IslandJobType.RESET_ISLAND) {
            return "BEFORE_RESET";
        }
        if (job.type() == IslandJobType.RESTORE_ISLAND) {
            return "BEFORE_RESTORE";
        }
        return "";
    }

    private BundleRestorePlan stageBundle(UUID islandId, ShardWorldManager.CellAssignment cell, long snapshotNo) throws IOException {
        if (worldRestorer != null) {
            return worldRestorer.stage(islandId, cell.worldName(), cell.originX(), cell.originZ(), snapshotNo);
        }
        return null;
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public record ActivationResult(boolean success, String state, UUID islandId, String worldName, int cellX, int cellZ, int originX, int originZ, int islandSize, long schemaVersion, long fencingToken, String extractedRoot, long preMutationSnapshotNo, String preMutationChecksum, long preMutationSizeBytes, String preMutationReason) {}
}
