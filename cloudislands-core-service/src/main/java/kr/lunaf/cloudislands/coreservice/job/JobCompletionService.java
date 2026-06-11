package kr.lunaf.cloudislands.coreservice.job;

import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class JobCompletionService {
    private final IslandRuntimeRepository runtimes;
    private final GlobalEventPublisher events;

    public JobCompletionService(IslandRuntimeRepository runtimes, GlobalEventPublisher events) {
        this.runtimes = runtimes;
        this.events = events;
    }

    public void completed(IslandJob job) {
        if (job.type() == IslandJobType.CREATE_ISLAND || job.type() == IslandJobType.ACTIVATE_ISLAND) {
            runtimes.markActive(job.islandId(), job.targetNode(), job.payload().getOrDefault("worldName", "ci_shard_001"), integer(job.payload().get("cellX")), integer(job.payload().get("cellZ")), longValue(job.payload().get("fencingToken")));
            events.publish(CloudIslandEventType.ISLAND_ACTIVATED.name(), Map.of("islandId", job.islandId().toString(), "nodeId", job.targetNode() == null ? "" : job.targetNode()));
            return;
        }
        if (job.type() == IslandJobType.DEACTIVATE_ISLAND || job.type() == IslandJobType.SAVE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
            runtimes.markInactive(job.islandId());
            events.publish(CloudIslandEventType.ISLAND_DEACTIVATED.name(), Map.of("islandId", job.islandId().toString()));
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
            case ACTIVATE_ISLAND, MIGRATE_ISLAND -> IslandState.ERROR_ACTIVATING;
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
