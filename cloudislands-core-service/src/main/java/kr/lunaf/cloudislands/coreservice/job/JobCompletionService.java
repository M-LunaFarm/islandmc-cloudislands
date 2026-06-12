package kr.lunaf.cloudislands.coreservice.job;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class JobCompletionService {
    private final IslandRuntimeRepository runtimes;
    private final GlobalEventPublisher events;
    private final IslandSnapshotRepository snapshots;
    private final RouteTicketStore tickets;
    private final IslandJobPublisher jobs;
    private final IslandRepository islands;
    private final PlayerProfileRepository playerProfiles;
    private final Duration routeTicketTtl;

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
        this.runtimes = runtimes;
        this.events = events;
        this.snapshots = snapshots;
        this.tickets = tickets;
        this.jobs = jobs;
        this.islands = islands;
        this.playerProfiles = playerProfiles;
        this.routeTicketTtl = routeTicketTtl == null || routeTicketTtl.isNegative() || routeTicketTtl.isZero() ? Duration.ofSeconds(30) : routeTicketTtl;
    }

    public void completed(IslandJob job) {
        if (job.type() == IslandJobType.CREATE_ISLAND || job.type() == IslandJobType.ACTIVATE_ISLAND || job.type() == IslandJobType.RESET_ISLAND) {
            if (job.type() == IslandJobType.RESET_ISLAND) {
                recordPreMutationSnapshot(job);
            }
            String worldName = job.payload().getOrDefault("worldName", "ci_shard_001");
            runtimes.markActive(job.islandId(), job.targetNode(), worldName, integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), longValue(job.payload().get("fencingToken")));
            int readyTickets = tickets.markReadyForIsland(job.islandId(), job.targetNode(), worldName, Instant.now().plus(routeTicketTtl), Map.of());
            events.publish(job.type() == IslandJobType.RESET_ISLAND ? CloudIslandEventType.ISLAND_RESET.name() : CloudIslandEventType.ISLAND_ACTIVATED.name(), Map.of("islandId", job.islandId().toString(), "nodeId", job.targetNode() == null ? "" : job.targetNode(), "readyTickets", Integer.toString(readyTickets)));
            return;
        }
        if (job.type() == IslandJobType.DEACTIVATE_ISLAND) {
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            if (snapshotNo > 0L) {
                snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("reason", job.type().name()), null, job.payload().getOrDefault("checksum", ""), longValue(job.payload().get("sizeBytes")));
                snapshots.prune(job.islandId(), 50);
            }
            runtimes.markInactive(job.islandId());
            publishMigrationActivation(job);
            events.publish(CloudIslandEventType.ISLAND_DEACTIVATED.name(), Map.of("islandId", job.islandId().toString()));
            return;
        }
        if (job.type() == IslandJobType.SAVE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            if (snapshotNo > 0L) {
                snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("reason", job.type().name()), null, job.payload().getOrDefault("checksum", ""), longValue(job.payload().get("sizeBytes")));
                snapshots.prune(job.islandId(), 50);
                events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), Map.of("islandId", job.islandId().toString(), "snapshotNo", Long.toString(snapshotNo), "reason", job.payload().getOrDefault("reason", "")));
            }
            return;
        }
        if (job.type() == IslandJobType.DELETE_ISLAND) {
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            if (snapshotNo > 0L) {
                snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/backups/delete-" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("reason", "DELETE_ISLAND"), null, job.payload().getOrDefault("checksum", ""), longValue(job.payload().get("sizeBytes")));
            }
            runtimes.setState(job.islandId(), IslandState.DELETED);
            events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", job.islandId().toString(), "snapshotNo", Long.toString(snapshotNo)));
            return;
        }
        if (job.type() == IslandJobType.RESTORE_ISLAND) {
            recordPreMutationSnapshot(job);
            restoreDeletedIslandRecord(job);
            runtimes.markActive(job.islandId(), job.targetNode(), job.payload().getOrDefault("worldName", "ci_shard_001"), integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), longValue(job.payload().get("fencingToken")));
            events.publish(CloudIslandEventType.ISLAND_RESTORED.name(), Map.of("islandId", job.islandId().toString(), "state", "RESTORED", "snapshotNo", job.payload().getOrDefault("snapshotNo", "")));
            return;
        }
        if (job.type() == IslandJobType.MIGRATE_ISLAND) {
            recordPreMutationSnapshot(job);
            String worldName = job.payload().getOrDefault("worldName", "ci_shard_001");
            runtimes.markActive(job.islandId(), job.targetNode(), worldName, integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), longValue(job.payload().get("fencingToken")));
            events.publish(CloudIslandEventType.ISLAND_MIGRATED.name(), Map.of("islandId", job.islandId().toString(), "targetNode", job.targetNode() == null ? "" : job.targetNode(), "worldName", worldName));
        }
    }

    public void failed(IslandJob job, String errorMessage) {
        IslandState state = switch (job.type()) {
            case CREATE_ISLAND -> IslandState.ERROR_CREATING;
            case ACTIVATE_ISLAND, MIGRATE_ISLAND, RESTORE_ISLAND, RESET_ISLAND -> IslandState.ERROR_ACTIVATING;
            case SAVE_ISLAND, SNAPSHOT_ISLAND, DEACTIVATE_ISLAND, DELETE_ISLAND -> IslandState.ERROR_SAVING;
            default -> IslandState.RECOVERY_REQUIRED;
        };
        runtimes.setState(job.islandId(), state);
        failPreparingRouteTickets(job, errorMessage);
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", job.islandId().toString(), "state", state.name(), "error", errorMessage == null ? "" : errorMessage));
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
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.MIGRATE_ISLAND, job.islandId(), targetNode, 10, Map.of("fencingToken", fencingToken), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_MIGRATE_REQUESTED.name(), Map.of("islandId", job.islandId().toString(), "targetNode", targetNode, "phase", "ACTIVATE_TARGET"));
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
