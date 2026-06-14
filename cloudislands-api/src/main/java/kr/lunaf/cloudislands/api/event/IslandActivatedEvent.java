package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandActivatedEvent(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, Instant occurredAt) implements CloudIslandEvent {
    public IslandActivatedEvent(UUID islandId, String nodeId, String worldName, Instant occurredAt) {
        this(islandId, nodeId, worldName, 0, 0, occurredAt);
    }
}
