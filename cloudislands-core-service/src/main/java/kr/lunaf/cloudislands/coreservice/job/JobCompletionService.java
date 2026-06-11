package kr.lunaf.cloudislands.coreservice.job;

import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
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

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events, IslandSnapshotRepository snapshots, RouteTicketStore tickets) {
        this.runtimes = runtimes;
        this.events = events;
        this.snapshots = snapshots;
        this.tickets = tickets;
    }

    public void completed(IslandJob job) {
        if (job.type() == IslandJobType.CREATE_ISLAND || job.type() == IslandJobType.ACTIVATE_ISLAND) {
            String worldName = job.payload().getOrDefault("worldName", "ci_shard_001");
            runtimes.markActive(job.islandId(), job.targetNode(), worldName, integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), longValue(job.payload().get("fencingToken")));
            int readyTickets = tickets.markReadyForIsland(job.islandId(), job.targetNode(), worldName, Map.of());
            events.publish(CloudIslandEventType.ISLAND_ACTIVATED.name(), Map.of("islandId", job.islandId().toString(), "nodeId", job.targetNode() == null ? "" : job.targetNode(), "readyTickets", Integer.toString(readyTickets)));
            return;
        }
        if (job.type() == IslandJobType.DEACTIVATE_ISLAND || job.type() == IslandJobType.SAVE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
            long snapshotNo = longValue(job.payload().get("snapshotNo"));
            if (snapshotNo > 0L) {
                snapshots.record(job.islandId(), snapshotNo, "islands/" + job.islandId() + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst", job.payload().getOrDefault("reason", job.type().name()), null, job.payload().getOrDefault("checksum", ""), longValue(job.payload().get("sizeBytes")));
                snapshots.prune(job.islandId(), 50);
            }
            runtimes.markInactive(job.islandId());
            events.publish(CloudIslandEventType.ISLAND_DEACTIVATED.name(), Map.of("islandId", job.islandId().toString()));
            return;
        }
        if (job.type() == IslandJobType.RESTORE_ISLAND) {
            runtimes.markActive(job.islandId(), job.targetNode(), job.payload().getOrDefault("worldName", "ci_shard_001"), integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), longValue(job.payload().get("fencingToken")));
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", job.islandId().toString(), "state", "RESTORED", "snapshotNo", job.payload().getOrDefault("snapshotNo", "")));
            return;
        }
        if (job.type() == IslandJobType.MIGRATE_ISLAND) {
            runtimes.setState(job.islandId(), IslandState.INACTIVE_READY);
            events.publish(CloudIslandEventType.ISLAND_MIGRATED.name(), Map.of("islandId", job.islandId().toString(), "targetNode", job.targetNode() == null ? "" : job.targetNode()));
        }
    }

    public void failed(IslandJob job, String errorMessage) {
        IslandState state = switch (job.type()) {
            case CREATE_ISLAND -> IslandState.ERROR_CREATING;
            case ACTIVATE_ISLAND, MIGRATE_ISLAND, RESTORE_ISLAND -> IslandState.ERROR_ACTIVATING;
            case SAVE_ISLAND, SNAPSHOT_ISLAND, DEACTIVATE_ISLAND -> IslandState.ERROR_SAVING;
            default -> IslandState.RECOVERY_REQUIRED;
        };
        runtimes.setState(job.islandId(), state);
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", job.islandId().toString(), "state", state.name(), "error", errorMessage == null ? "" : errorMessage));
    }

    private int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
