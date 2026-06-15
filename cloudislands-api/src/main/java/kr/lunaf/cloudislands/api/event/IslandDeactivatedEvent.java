package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeactivatedEvent(UUID islandId, String nodeId, String targetNode, String phase, long snapshotNo, Instant occurredAt) implements CloudIslandEvent {
    public IslandDeactivatedEvent(UUID islandId, String nodeId, long snapshotNo, Instant occurredAt) {
        this(islandId, nodeId, "", "", snapshotNo, occurredAt);
    }
}
