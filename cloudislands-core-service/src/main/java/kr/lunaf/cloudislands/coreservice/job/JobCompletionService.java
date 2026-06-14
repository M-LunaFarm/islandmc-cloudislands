package kr.lunaf.cloudislands.coreservice.job;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.RedisActivationLock;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class JobCompletionService {
    private final IslandRuntimeRepository runtimes;
    private final GlobalEventPublisher events;
    private final IslandSnapshotRepository snapshots;
    private final RouteTicketStore tickets;
    private final IslandJobPublisher jobs;
    private final IslandRepository islands;
    private final PlayerProfileRepository playerProfiles;
    private final Duration routeTicketTtl;
    private final int snapshotKeepLatest;
    private final SnapshotRetentionPolicy snapshotRetentionPolicy;
    private final RedisActivationLock activationLock;

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets) {
        this(runtimes, events, snapshots, tickets, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs) {
        this(runtimes, events, snapshots, tickets, jobs, null, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, Duration.ofSeconds(30));
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, 85);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, int snapshotKeepLatest) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, snapshotKeepLatest, null);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, int snapshotKeepLatest, RedisActivationLock activationLock) {
        this(runtimes, events, snapshots, tickets, jobs, islands, playerProfiles, routeTicketTtl, new SnapshotRetentionPolicy(snapshotKeepLatest, 0, 0, 0, true, "SHA-256"), activationLock);
    }

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets, IslandJobPublisher jobs, IslandRepository islands, PlayerProfileRepository playerProfiles, Duration routeTicketTtl, SnapshotRetentionPolicy snapshotRetentionPolicy, RedisActivationLock activationLock) {
        this.runtimes = runtimes;
        this.events = events;
        this.snapshots = snapshots;
        this.tickets = tickets;
        this.jobs = jobs;
        this.islands = islands;
        this.playerProfiles = playerProfiles;
        this.routeTicketTtl = routeTicketTtl == null || routeTicketTtl.isNegative() || routeTicketTtl.isZero() ? Duration.ofSeconds(30) : routeTicketTtl;
        this.snapshotRetentionPolicy = snapshotRetentionPolicy == null ? SnapshotRetentionPolicy.defaultPolicy() : snapshotRetentionPolicy.normalized();
        this.snapshotKeepLatest = this.snapshotRetentionPolicy.retainedSnapshotCount();
        this.activationLock = activationLock;
    }

    public void completed(IslandJob job) {
        if (job.type() == IslandJobType.CREATE_ISLAND || job.type() == IslandJobType.ACTIVATE_ISLAND || job.type() == IslandJobType.RESET_ISLAND) {
            String worldName = job.payload().getOrDefault("worldName", "ci_shard_001");
            if (!markActiveFromJob(job, worldName)) {
                return;
            }
            if (job.type() == IslandJobType.RESET_ISLAND) {
                recordPreMutationSnapshot(job);
            }
            if (job.type() == IslandJobType.CREATE_ISLAND) {
                recordCompletedSnapshot(job, "CREATED", true);
            }
            setIslandState(job.islandId(), IslandState.ACTIVE);
            int readyTickets = tickets.markReadyForIsland(job.islandId(), job.targetNode(), worldName, Instant.now().plus(routeTicketTtl), Map.of());
            events.publish(job.type() == IslandJobType.RESET_ISLAND ? CloudIslandEventType.ISLAND_RESET.name() : CloudIslandEventType.ISLAND_ACTIVATED.name(), Map.of("islandId", job.islandId().toString(), "nodeId", job.targetNode() == null ? "" : job.targetNode(), "readyTickets", Integer.toString(readyTickets)));
            return;
        }
        if (job.type() == IslandJobType.DEACTIVATE_ISLAND) {
            if (job.payload().containsKey("migrateTargetNode")) {
                completeMigrationSourceSave(job);
                return;
            }
            if (!markInactiveFromJob(job)) {
                return;
            }
            long snapshotNo = recordCompletedSnapshot(job, job.type().name(), true);
            setIslandState(job.islandId(), IslandState.INACTIVE_READY);
            publishMigrationActivation(job);
            events.publish(CloudIslandEventType.ISLAND_DEACTIVATED.name(), Map.of("islandId", job.islandId().toString()));
            return;
        }
        if (job.type() == IslandJobType.SAVE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
            if (isStaleSnapshotCompletion(job)) {
                return;
            }
            long snapshotNo = recordCompletedSnapshot(job, job.type().name(), true);
            if (snapshotNo > 0L) {
                events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), Map.of("islandId", job.islandId().toString(), "snapshotNo", Long.toString(snapshotNo), "reason", job.payload().getOrDefault("reason", "")));
            }
            return;
        }
        if (job.type() == IslandJobType.DELETE_ISLAND) {
            if (isStaleSnapshotCompletion(job)) {
                return;
            }
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            runtimes.setState(job.islandId(), IslandState.BACKUP_BEFORE_DELETE);
            setIslandState(job.islandId(), IslandState.BACKUP_BEFORE_DELETE);
            if (snapshotNo > 0L) {
                snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/backups/delete-" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("reason", "DELETE_ISLAND"), null, job.payload().getOrDefault("checksum", ""), longValue(job.payload().get("sizeBytes")));
                snapshots.prune(job.islandId(), snapshotRetentionPolicy);
            }
            runtimes.setState(job.islandId(), IslandState.DELETING);
            setIslandState(job.islandId(), IslandState.DELETING);
            runtimes.setState(job.islandId(), IslandState.DELETED);
            markIslandDeleted(job);
            clearOwnerPrimaryIsland(job);
            events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", job.islandId().toString(), "snapshotNo", Long.toString(snapshotNo)));
            return;
        }
        if (job.type() == IslandJobType.RESTORE_ISLAND) {
            if (!markActiveFromJob(job, job.payload().getOrDefault("worldName", "ci_shard_001"))) {
                return;
            }
            recordPreMutationSnapshot(job);
            restoreDeletedIslandRecord(job);
            setIslandState(job.islandId(), IslandState.ACTIVE);
            events.publish(CloudIslandEventType.ISLAND_RESTORED.name(), Map.of("islandId", job.islandId().toString(), "state", "RESTORED", "snapshotNo", job.payload().getOrDefault("snapshotNo", "")));
            return;
        }
        if (job.type() == IslandJobType.MIGRATE_ISLAND) {
            String worldName = job.payload().getOrDefault("worldName", "ci_shard_001");
            if (!markActiveFromJob(job, worldName)) {
                return;
            }
            recordPreMutationSnapshot(job);
            setIslandState(job.islandId(), IslandState.ACTIVE);
            int readyTickets = tickets.markReadyForIsland(job.islandId(), job.targetNode(), worldName, Instant.now().plus(routeTicketTtl), Map.of(
                "migrationReturnReady", "true",
                "sourceNode", job.payload().getOrDefault("sourceNode", "")
            ));
            events.publish(CloudIslandEventType.ISLAND_MIGRATED.name(), Map.of(
                "islandId", job.islandId().toString(),
                "fromNode", job.payload().getOrDefault("sourceNode", ""),
                "targetNode", job.targetNode() == null ? "" : job.targetNode(),
                "worldName", worldName,
                "fencingToken", job.payload().getOrDefault("fencingToken", "0"),
                "readyTickets", Integer.toString(readyTickets)
            ));
            releaseMigrationLock(job);
        }
    }

    public void failed(IslandJob job, String errorMessage) {
        if (isStaleFencingFailure(job, errorMessage)) {
            return;
        }
        if (job.type() == IslandJobType.SAVE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
            preserveRuntimeOnSnapshotFailure(job, errorMessage);
            return;
        }
        if (job.type() == IslandJobType.DEACTIVATE_ISLAND) {
            preserveRuntimeOnDeactivationFailure(job, errorMessage);
            return;
        }
        IslandState state = switch (job.type()) {
            case CREATE_ISLAND -> IslandState.ERROR_CREATING;
            case ACTIVATE_ISLAND, MIGRATE_ISLAND, RESTORE_ISLAND, RESET_ISLAND -> IslandState.ERROR_ACTIVATING;
            case SAVE_ISLAND, SNAPSHOT_ISLAND, DEACTIVATE_ISLAND, DELETE_ISLAND -> IslandState.ERROR_SAVING;
            default -> IslandState.RECOVERY_REQUIRED;
        };
        runtimes.setState(job.islandId(), state);
        setIslandState(job.islandId(), state);
        failPreparingRouteTickets(job, errorMessage);
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", job.islandId().toString(), "state", state.name(), "error", errorMessage == null ? "" : errorMessage));
        if (job.type() == IslandJobType.MIGRATE_ISLAND || (job.type() == IslandJobType.DEACTIVATE_ISLAND && job.payload().containsKey("migrateTargetNode"))) {
            releaseMigrationLock(job);
        }
    }

    private void preserveRuntimeOnSnapshotFailure(IslandJob job, String errorMessage) {
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        if (current == null || current.state() != IslandState.ACTIVE) {
            runtimes.setState(job.islandId(), IslandState.ERROR_SAVING);
            setIslandState(job.islandId(), IslandState.ERROR_SAVING);
        }
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "state", current == null ? IslandState.ERROR_SAVING.name() : current.state().name(),
            "jobType", job.type().name(),
            "error", errorMessage == null ? "" : errorMessage,
            "runtimePreserved", Boolean.toString(current != null && current.state() == IslandState.ACTIVE)
        ));
    }

    private void preserveRuntimeOnDeactivationFailure(IslandJob job, String errorMessage) {
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        boolean migrationSource = job.payload().containsKey("migrateTargetNode");
        IslandState keptState = migrationSource ? IslandState.DEACTIVATING : IslandState.ACTIVE;
        if (current == null || current.activeNode() == null || current.activeNode().isBlank()) {
            runtimes.setState(job.islandId(), IslandState.ERROR_SAVING);
            setIslandState(job.islandId(), IslandState.ERROR_SAVING);
            keptState = IslandState.ERROR_SAVING;
        } else {
            runtimes.setState(job.islandId(), keptState);
            setIslandState(job.islandId(), keptState);
        }
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "state", keptState.name(),
            "jobType", job.type().name(),
            "error", errorMessage == null ? "" : errorMessage,
            "runtimePreserved", Boolean.toString(keptState != IslandState.ERROR_SAVING),
            "phase", migrationSource ? "MIGRATION_SOURCE_SAVE_FAILED" : "DEACTIVATION_SAVE_FAILED"
        ));
        if (migrationSource) {
            failPreparingRouteTickets(job, errorMessage);
        }
    }

    private boolean isStaleFencingFailure(IslandJob job, String errorMessage) {
        long fencingToken = longValue(job.payload().get("fencingToken"));
        if (fencingToken <= 0L) {
            return false;
        }
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        String staleReason = staleCompletionReason(job, current, fencingToken);
        if (staleReason.isBlank()) {
            return false;
        }
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "state", current.state().name(),
            "ignoredJob", job.jobId().toString(),
            "jobFencingToken", Long.toString(fencingToken),
            "currentFencingToken", Long.toString(current.fencingToken()),
            "reason", staleReason,
            "error", errorMessage == null ? "" : errorMessage
        ));
        return true;
    }

    private boolean isStaleSnapshotCompletion(IslandJob job) {
        long fencingToken = longValue(job.payload().get("fencingToken"));
        if (fencingToken <= 0L) {
            return false;
        }
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        String staleReason = staleCompletionReason(job, current, fencingToken);
        if (staleReason.isBlank()) {
            return false;
        }
        publishIgnoredCompletion(job, current, fencingToken, staleReason);
        return true;
    }

    private void releaseMigrationLock(IslandJob job) {
        if (activationLock != null) {
            activationLock.releaseIfOwner(job.islandId(), "migrate");
        }
    }

    private void completeMigrationSourceSave(IslandJob job) {
        long fencingToken = longValue(job.payload().get("fencingToken"));
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        String staleReason = staleCompletionReason(job, current, fencingToken);
        if (!staleReason.isBlank()) {
            publishIgnoredCompletion(job, current, fencingToken, staleReason);
            return;
        }
        long snapshotNo = recordCompletedSnapshot(job, job.type().name(), true);
        runtimes.setState(job.islandId(), IslandState.ACTIVATING);
        setIslandState(job.islandId(), IslandState.ACTIVATING);
        publishMigrationActivation(job);
        events.publish(CloudIslandEventType.ISLAND_DEACTIVATED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "phase", "MIGRATION_SOURCE_SAVED",
            "targetNode", job.payload().getOrDefault("migrateTargetNode", ""),
            "snapshotNo", Long.toString(snapshotNo)
        ));
    }

    private void setIslandState(UUID islandId, IslandState state) {
        if (islands != null) {
            islands.setState(islandId, state);
        }
    }

    private void markIslandDeleted(IslandJob job) {
        if (islands == null) {
            return;
        }
        UUID ownerUuid = ownerUuid(job);
        if (ownerUuid != null) {
            islands.markDeleted(job.islandId(), ownerUuid);
        } else {
            setIslandState(job.islandId(), IslandState.DELETED);
        }
    }

    private void clearOwnerPrimaryIsland(IslandJob job) {
        if (playerProfiles == null) {
            return;
        }
        UUID ownerUuid = ownerUuid(job);
        if (ownerUuid != null) {
            playerProfiles.clearPrimaryIsland(ownerUuid);
        }
    }

    private UUID ownerUuid(IslandJob job) {
        String value = job.payload().getOrDefault("ownerUuid", "");
        if (!value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (islands == null) {
            return null;
        }
        return islands.findById(job.islandId()).map(kr.lunaf.cloudislands.api.model.IslandSnapshot::ownerUuid).orElse(null);
    }

    private boolean markActiveFromJob(IslandJob job, String worldName) {
        long fencingToken = longValue(job.payload().get("fencingToken"));
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        String staleReason = staleCompletionReason(job, current, fencingToken);
        if (!staleReason.isBlank()) {
            failPreparingRouteTickets(job, staleReason);
            publishIgnoredCompletion(job, current, fencingToken, staleReason);
            return false;
        }
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime = runtimes.markActive(job.islandId(), job.targetNode(), worldName, integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), fencingToken);
        if (runtime.fencingToken() == fencingToken) {
            return true;
        }
        failPreparingRouteTickets(job, "STALE_FENCING_TOKEN");
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "state", runtime.state().name(),
            "ignoredJob", job.jobId().toString(),
            "jobFencingToken", Long.toString(fencingToken),
            "currentFencingToken", Long.toString(runtime.fencingToken())
        ));
        return false;
    }

    private boolean markInactiveFromJob(IslandJob job) {
        long fencingToken = longValue(job.payload().get("fencingToken"));
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current = runtimes.find(job.islandId()).orElse(null);
        String staleReason = staleCompletionReason(job, current, fencingToken);
        if (!staleReason.isBlank()) {
            failPreparingRouteTickets(job, staleReason);
            publishIgnoredCompletion(job, current, fencingToken, staleReason);
            return false;
        }
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime = runtimes.markInactive(job.islandId(), fencingToken);
        if (runtime.fencingToken() == fencingToken && runtime.state() == IslandState.INACTIVE_READY) {
            return true;
        }
        failPreparingRouteTickets(job, "STALE_FENCING_TOKEN");
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "state", runtime.state().name(),
            "ignoredJob", job.jobId().toString(),
            "jobFencingToken", Long.toString(fencingToken),
            "currentFencingToken", Long.toString(runtime.fencingToken())
        ));
        return false;
    }

    private String staleCompletionReason(IslandJob job, kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current, long fencingToken) {
        if (current == null || fencingToken <= 0L) {
            return "";
        }
        if (current.fencingToken() > fencingToken) {
            return "STALE_FENCING_TOKEN";
        }
        if (current.state() == IslandState.RECOVERY_REQUIRED || current.state() == IslandState.QUARANTINED || current.state() == IslandState.DELETED) {
            return "RUNTIME_NOT_ACCEPTING_COMPLETION";
        }
        if (isMigrationSourceDeactivation(job, current)) {
            return "";
        }
        if (current.fencingToken() == fencingToken && !sameNode(job.targetNode(), current.leaseOwner(), current.activeNode())) {
            return "STALE_NODE_COMPLETION";
        }
        return "";
    }

    private boolean isMigrationSourceDeactivation(IslandJob job, kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current) {
        if (job.type() != IslandJobType.DEACTIVATE_ISLAND || current == null || current.state() != IslandState.DEACTIVATING) {
            return false;
        }
        String migrateTargetNode = job.payload().getOrDefault("migrateTargetNode", "");
        return !migrateTargetNode.isBlank() && sameNode(migrateTargetNode, current.leaseOwner(), current.activeNode());
    }

    private boolean sameNode(String jobNode, String leaseOwner, String activeNode) {
        if (jobNode == null || jobNode.isBlank()) {
            return true;
        }
        String owner = leaseOwner == null || leaseOwner.isBlank() ? activeNode : leaseOwner;
        return owner == null || owner.isBlank() || owner.equals(jobNode);
    }

    private void publishIgnoredCompletion(IslandJob job, kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot current, long fencingToken, String reason) {
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "state", current == null ? "" : current.state().name(),
            "ignoredJob", job.jobId().toString(),
            "targetNode", job.targetNode() == null ? "" : job.targetNode(),
            "leaseOwner", current == null || current.leaseOwner() == null ? "" : current.leaseOwner(),
            "activeNode", current == null || current.activeNode() == null ? "" : current.activeNode(),
            "jobFencingToken", Long.toString(fencingToken),
            "currentFencingToken", current == null ? "0" : Long.toString(current.fencingToken()),
            "reason", reason
        ));
    }

    private void failPreparingRouteTickets(IslandJob job, String errorMessage) {
        if (job.type() != IslandJobType.CREATE_ISLAND && job.type() != IslandJobType.ACTIVATE_ISLAND && job.type() != IslandJobType.MIGRATE_ISLAND && job.type() != IslandJobType.RESTORE_ISLAND && job.type() != IslandJobType.RESET_ISLAND) {
            return;
        }
        String reason = errorMessage == null || errorMessage.isBlank() ? "JOB_FAILED" : errorMessage;
        for (var ticket : tickets.markFailedForIsland(job.islandId(), job.targetNode(), reason)) {
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                "ticketId", ticket.ticketId().toString(),
                "playerUuid", ticket.playerUuid().toString(),
                "islandId", ticket.islandId().toString(),
                "action", ticket.action().name(),
                "targetNode", ticket.targetNode(),
                "reason", reason
            ));
        }
    }

    private int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void recordPreMutationSnapshot(IslandJob job) {
        long snapshotNo = longValue(job.payload().get("preMutationSnapshotNo"));
        if (snapshotNo <= 0L) {
            return;
        }
        snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("preMutationReason", "BEFORE_MUTATION"), null, job.payload().getOrDefault("preMutationChecksum", ""), longValue(job.payload().get("preMutationSizeBytes")));
        snapshots.prune(job.islandId(), snapshotRetentionPolicy);
    }

    private long recordCompletedSnapshot(IslandJob job, String fallbackReason, boolean prune) {
        long snapshotNo = longValue(job.payload().get("snapshotNo"));
        if (snapshotNo <= 0L) {
            return 0L;
        }
        snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("reason", fallbackReason), null, job.payload().getOrDefault("checksum", ""), longValue(job.payload().get("sizeBytes")));
        if (prune) {
            snapshots.prune(job.islandId(), snapshotRetentionPolicy);
        }
        return snapshotNo;
    }

    private void restoreDeletedIslandRecord(IslandJob job) {
        if (islands == null) {
            return;
        }
        islands.restoreDeleted(job.islandId()).ifPresent(island -> {
            islands.createOwnerMember(island.islandId(), island.ownerUuid());
            if (playerProfiles != null && playerProfiles.find(island.ownerUuid()).primaryIslandId().isEmpty()) {
                playerProfiles.setPrimaryIsland(island.ownerUuid(), island.islandId());
            }
        });
    }

    private void publishMigrationActivation(IslandJob job) {
        if (jobs == null) {
            return;
        }
        String targetNode = job.payload().getOrDefault("migrateTargetNode", "");
        if (targetNode.isBlank()) {
            return;
        }
        String fencingToken = job.payload().getOrDefault("migrationFencingToken", job.payload().getOrDefault("fencingToken", "0"));
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("fencingToken", fencingToken);
        payload.put("sourceNode", job.targetNode() == null ? "" : job.targetNode());
        payload.put("worldName", job.payload().getOrDefault("worldName", "ci_shard_001"));
        payload.put("cellX", job.payload().getOrDefault("cellX", "0"));
        payload.put("cellZ", job.payload().getOrDefault("cellZ", "0"));
        payload.put("snapshotNo", job.payload().getOrDefault("snapshotNo", "0"));
        payload.put("sourceSnapshotReason", job.payload().getOrDefault("reason", "BEFORE_MIGRATION"));
        payload.put("sourceSnapshotChecksum", job.payload().getOrDefault("checksum", ""));
        payload.put("sourceSnapshotSizeBytes", job.payload().getOrDefault("sizeBytes", "0"));
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.MIGRATE_ISLAND, job.islandId(), targetNode, 10, Map.copyOf(payload), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_MIGRATE_REQUESTED.name(), Map.of(
            "islandId", job.islandId().toString(),
            "sourceNode", job.targetNode() == null ? "" : job.targetNode(),
            "targetNode", targetNode,
            "phase", "ACTIVATE_TARGET",
            "worldName", payload.get("worldName"),
            "fencingToken", fencingToken
        ));
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
