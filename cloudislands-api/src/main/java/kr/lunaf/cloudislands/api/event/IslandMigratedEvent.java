package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMigratedEvent(UUID islandId, String fromNode, String toNode, String worldName, int cellX, int cellZ, long fencingToken, Instant occurredAt) implements CloudIslandEvent {
    public IslandMigratedEvent(UUID islandId, String fromNode, String toNode, String worldName, long fencingToken, Instant occurredAt) {
        this(islandId, fromNode, toNode, worldName, 0, 0, fencingToken, occurredAt);
    }
}
