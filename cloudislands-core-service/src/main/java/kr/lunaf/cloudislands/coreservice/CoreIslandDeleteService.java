package kr.lunaf.cloudislands.coreservice;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

final class CoreIslandDeleteService {
    private final IslandStorage deleteStorage;
    private final IslandRepository islandRepository;
    private final PlayerProfileRepository playerProfiles;
    private final IslandRuntimeRepository runtimeRepository;
    private final IslandJobQueue jobs;
    private final GlobalEventPublisher events;
    private final IslandSnapshotRepository snapshotRepository;
    private final SnapshotRetentionPolicy snapshotRetentionPolicy;

    CoreIslandDeleteService(
            IslandStorage deleteStorage,
            IslandRepository islandRepository,
            PlayerProfileRepository playerProfiles,
            IslandRuntimeRepository runtimeRepository,
            IslandJobQueue jobs,
            GlobalEventPublisher events,
            IslandSnapshotRepository snapshotRepository,
            SnapshotRetentionPolicy snapshotRetentionPolicy) {
        this.deleteStorage = deleteStorage;
        this.islandRepository = islandRepository;
        this.playerProfiles = playerProfiles;
        this.runtimeRepository = runtimeRepository;
        this.jobs = jobs;
        this.events = events;
        this.snapshotRepository = snapshotRepository;
        this.snapshotRetentionPolicy = snapshotRetentionPolicy;
    }

    DeleteIslandResult requestIslandDelete(UUID islandId, UUID ownerUuid, UUID requesterUuid, String reason) {
        java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
        if (island.isEmpty() || !island.get().ownerUuid().equals(ownerUuid)) {
            return new DeleteIslandResult(false, "NOT_OWNER_OR_MISSING", islandId);
        }
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETE_REQUESTED);
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETE_REQUESTED);
        String code = publishDeleteJobOrMarkDeleted(islandId, ownerUuid, reason);
        if ("DELETED".equals(code)) {
            playerProfiles.clearPrimaryIsland(ownerUuid);
            events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", islandId.toString(), "requesterUuid", requesterUuid.toString()));
        }
        return new DeleteIslandResult(deleteAccepted(code), code, islandId);
    }

    private boolean deleteAccepted(String code) {
        return "DELETE_REQUESTED".equals(code) || "DELETED".equals(code);
    }

    private String publishDeleteJobOrMarkDeleted(UUID islandId, UUID ownerUuid, String reason) {
        java.util.Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> runtime = runtimeRepository.find(islandId);
        String targetNode = runtime.map(kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot::activeNode).orElse("");
        if (targetNode != null && !targetNode.isBlank()) {
            String fencingToken = runtime.map(value -> Long.toString(value.fencingToken())).orElse("0");
            islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DEACTIVATING);
            runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DEACTIVATING);
            try {
                jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DELETE_ISLAND, islandId, targetNode, 50, Map.of("reason", "BEFORE_DELETE", "deleteReason", reason, "ownerUuid", ownerUuid.toString(), "fencingToken", fencingToken), java.time.Instant.now()));
                events.publish(CloudIslandEventType.ISLAND_DELETE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "reason", reason, "snapshotReason", "BEFORE_DELETE"));
                return "DELETE_REQUESTED";
            } catch (RuntimeException exception) {
                islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.RECOVERY_REQUIRED);
                runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.RECOVERY_REQUIRED);
                events.publish(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "reason", "delete-job-publish-failed"));
                return "DELETE_QUEUE_FAILED";
            }
        }
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DEACTIVATING);
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DEACTIVATING);
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.BACKUP_BEFORE_DELETE);
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.BACKUP_BEFORE_DELETE);
        if (!backupInactiveStorageBeforeDelete(islandId, reason)) {
            islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.RECOVERY_REQUIRED);
            runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.RECOVERY_REQUIRED);
            events.publish(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), Map.of("islandId", islandId.toString(), "reason", "delete-backup-failed"));
            return "DELETE_BACKUP_FAILED";
        }
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETING);
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETING);
        return islandRepository.markDeleted(islandId, ownerUuid) ? "DELETED" : "DELETE_MARK_FAILED";
    }

    private boolean backupInactiveStorageBeforeDelete(UUID islandId, String reason) {
        if (deleteStorage == null) {
            return true;
        }
        try {
            long snapshotNo = System.currentTimeMillis();
            IslandStorage.StoredBundle storedBundle = deleteStorage.writeDeleteBackupFromLatest(islandId, snapshotNo, "BEFORE_DELETE");
            String storagePath = storedBundle.storagePath() == null || storedBundle.storagePath().isBlank()
                ? "islands/" + islandId + "/backups/delete-" + String.format("%06d", snapshotNo) + "/bundle.tar.zst"
                : storedBundle.storagePath();
            recordSnapshotAndPublish(islandId, snapshotNo, storagePath, "BEFORE_DELETE", storedBundle.checksum(), storedBundle.sizeBytes(), "");
            deleteStorage.deleteLiveState(islandId);
            return true;
        } catch (IOException exception) {
            events.publish(CloudIslandEventType.ISLAND_DELETE_BACKUP_FAILED.name(), Map.of("islandId", islandId.toString(), "reason", reason, "error", exception.getMessage() == null ? "" : exception.getMessage()));
            return false;
        }
    }

    private int recordSnapshotAndPublish(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId) {
        return recordSnapshotAndPublish(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId, 0L);
    }

    private int recordSnapshotAndPublish(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        snapshotRepository.record(islandId, snapshotNo, storagePath, reason, null, checksum, sizeBytes);
        int pruned = snapshotRepository.prune(islandId, snapshotRetentionPolicy);
        events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), Map.of(
            "islandId", islandId.toString(),
            "snapshotNo", Long.toString(snapshotNo),
            "reason", reason == null ? "" : reason,
            "storagePath", storagePath == null ? "" : storagePath,
            "checksum", checksum == null ? "" : checksum,
            "sizeBytes", Long.toString(sizeBytes),
            "nodeId", nodeId == null ? "" : nodeId,
            "fencingToken", Long.toString(fencingToken),
            "pruned", Integer.toString(pruned)
        ));
        return pruned;
    }
}
