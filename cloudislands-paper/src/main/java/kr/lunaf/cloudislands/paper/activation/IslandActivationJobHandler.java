package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
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
    private final int defaultIslandSize;

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
        this(storage, shardWorldManager, protectionController, worldRestorer, preloader, preloadRadius, cellTransfer, activeIslands, saveService, 300);
    }

    public IslandActivationJobHandler(IslandStorage storage, ShardWorldManager shardWorldManager, ProtectionController protectionController, IslandWorldRestorer worldRestorer, ShardWorldPreloader preloader, int preloadRadius, FileBackedCellTransfer cellTransfer, ActiveIslandRegistry activeIslands, IslandSaveService saveService, int defaultIslandSize) {
        this.storage = storage;
        this.shardWorldManager = shardWorldManager;
        this.protectionController = protectionController;
        this.worldRestorer = worldRestorer;
        this.preloader = preloader;
        this.preloadRadius = preloadRadius;
        this.cellTransfer = cellTransfer;
        this.activeIslands = activeIslands;
        this.saveService = saveService;
        this.defaultIslandSize = Math.max(1, defaultIslandSize);
    }

    public ActivationResult handle(IslandJob job) {
        if (job.type() != IslandJobType.ACTIVATE_ISLAND && job.type() != IslandJobType.CREATE_ISLAND && job.type() != IslandJobType.MIGRATE_ISLAND && job.type() != IslandJobType.RESTORE_ISLAND && job.type() != IslandJobType.RESET_ISLAND) {
            return new ActivationResult(false, "UNSUPPORTED_JOB", null, null, 0, 0, 0, 0, 0, 0L, 0L, null, 0L, "", 0L, "", 0L, "", 0L);
        }
        UUID islandId = job.islandId();
        try {
            IslandBundleManifest manifest = manifestFor(job, islandId);
            IslandSaveService.SaveResult preMutationSnapshot = snapshotBeforeMutation(job);
            ShardWorldManager.CellAssignment cell = shardWorldManager.allocateCell(islandId);
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            String storagePath = job.payload().getOrDefault("storagePath", "");
            BundleRestorePlan restorePlan = stageBundle(job, islandId, cell, snapshotNo, storagePath);
            if (restorePlan != null && cellTransfer != null) {
                CellPlacementPlan placement = new ShardCellTransferPlanner(manifest.size()).placement(restorePlan);
                cellTransfer.place(placement);
            }
            if (job.type() == IslandJobType.RESTORE_ISLAND && snapshotNo > 0L) {
                if (storagePath.isBlank()) {
                    storage.promoteSnapshot(islandId, snapshotNo);
                } else {
                    storage.promoteBundle(islandId, snapshotNo, storagePath);
                }
            }
            if (preloader != null) {
                preloader.preload(cell.worldName(), cell.originX(), cell.originZ(), preloadRadius);
            }
            protectionController.registerIsland(islandId, cell.worldName(), cell.originX(), cell.originZ(), manifest.size(), cell.cellX(), cell.cellZ());
            IslandSaveService.SaveResult creationSnapshot = snapshotAfterCreate(job, cell, manifest);
            return new ActivationResult(true, "ACTIVE", islandId, cell.worldName(), cell.cellX(), cell.cellZ(), cell.originX(), cell.originZ(), manifest.size(), manifest.schemaVersion(), longValue(job.payload().get("fencingToken")), restorePlan == null ? null : restorePlan.extractedRoot().toString(), preMutationSnapshot == null ? 0L : preMutationSnapshot.snapshotNo(), preMutationSnapshot == null ? "" : preMutationSnapshot.checksum(), preMutationSnapshot == null ? 0L : preMutationSnapshot.sizeBytes(), preMutationReason(job), creationSnapshot == null ? 0L : creationSnapshot.snapshotNo(), creationSnapshot == null ? "" : creationSnapshot.checksum(), creationSnapshot == null ? 0L : creationSnapshot.sizeBytes());
        } catch (Exception exception) {
            return new ActivationResult(false, "ERROR_ACTIVATING", islandId, null, 0, 0, 0, 0, 0, 0L, 0L, null, 0L, "", 0L, "", 0L, "", 0L);
        }
    }

    private IslandBundleManifest manifestFor(IslandJob job, UUID islandId) throws IOException {
        try {
            return storage.readManifest(islandId);
        } catch (IOException exception) {
            if (job.type() != IslandJobType.CREATE_ISLAND) {
                throw exception;
            }
            Instant now = Instant.now();
            int size = intValue(job.payload().get("islandSize"), defaultIslandSize);
            return new IslandBundleManifest(
                islandId,
                uuidValue(job.payload().get("ownerUuid")),
                3,
                "1.21.11",
                12,
                Math.max(1, size),
                new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
                now,
                now,
                ""
            );
        }
    }

    private IslandSaveService.SaveResult snapshotAfterCreate(IslandJob job, ShardWorldManager.CellAssignment cell, IslandBundleManifest manifest) throws IOException {
        if (job.type() != IslandJobType.CREATE_ISLAND || saveService == null) {
            return null;
        }
        ActiveIslandRegistry.ActiveIsland activeIsland = new ActiveIslandRegistry.ActiveIsland(job.islandId(), cell.worldName(), cell.cellX(), cell.cellZ(), cell.originX(), cell.originZ(), manifest.size(), manifest.schemaVersion(), Instant.now());
        return saveService.save(job.islandId(), activeIsland, manifest, "CREATED");
    }

    private IslandSaveService.SaveResult snapshotBeforeMutation(IslandJob job) throws IOException {
        if ((job.type() != IslandJobType.RESTORE_ISLAND && job.type() != IslandJobType.RESET_ISLAND && job.type() != IslandJobType.MIGRATE_ISLAND) || activeIslands == null || saveService == null) {
            return null;
        }
        ActiveIslandRegistry.ActiveIsland activeIsland = activeIslands.find(job.islandId()).orElse(null);
        if (activeIsland == null) {
            return null;
        }
        if (job.type() == IslandJobType.RESET_ISLAND) {
            return saveService.snapshotBeforeReset(job.islandId(), activeIsland);
        }
        if (job.type() == IslandJobType.MIGRATE_ISLAND) {
            return saveService.snapshotBeforeMigration(job.islandId(), activeIsland);
        }
        return saveService.snapshotBeforeRestore(job.islandId(), activeIsland);
    }

    private String preMutationReason(IslandJob job) {
        if (job.type() == IslandJobType.MIGRATE_ISLAND) {
            return "BEFORE_MIGRATION";
        }
        if (job.type() == IslandJobType.RESET_ISLAND) {
            return "BEFORE_RESET";
        }
        if (job.type() == IslandJobType.RESTORE_ISLAND) {
            return "BEFORE_RESTORE";
        }
        return "";
    }

    private BundleRestorePlan stageBundle(IslandJob job, UUID islandId, ShardWorldManager.CellAssignment cell, long snapshotNo, String storagePath) throws IOException {
        if (job.type() == IslandJobType.CREATE_ISLAND && snapshotNo <= 0L && (storagePath == null || storagePath.isBlank())) {
            return null;
        }
        if (worldRestorer != null) {
            return worldRestorer.stage(islandId, cell.worldName(), cell.originX(), cell.originZ(), snapshotNo, storagePath);
        }
        return null;
    }

    private int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private UUID uuidValue(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return new UUID(0L, 0L);
        }
    }

    public record ActivationResult(boolean success, String state, UUID islandId, String worldName, int cellX, int cellZ, int originX, int originZ, int islandSize, long schemaVersion, long fencingToken, String extractedRoot, long preMutationSnapshotNo, String preMutationChecksum, long preMutationSizeBytes, String preMutationReason, long creationSnapshotNo, String creationSnapshotChecksum, long creationSnapshotSizeBytes) {}
}
