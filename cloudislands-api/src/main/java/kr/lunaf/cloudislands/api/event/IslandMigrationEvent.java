package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMigrationEvent(UUID islandId, boolean requested, String sourceNode, String targetNode, String phase, String worldName, int cellX, int cellZ, long fencingToken, String placementSource, Instant occurredAt) implements CloudIslandEvent {
    public IslandMigrationEvent(UUID islandId, boolean requested, String targetNode, String phase, String worldName, long fencingToken, Instant occurredAt) {
        this(islandId, requested, "", targetNode, phase, worldName, 0, 0, fencingToken, "", occurredAt);
    }

    public IslandMigrationEvent(UUID islandId, boolean requested, String sourceNode, String targetNode, String phase, String worldName, int cellX, int cellZ, long fencingToken, Instant occurredAt) {
        this(islandId, requested, sourceNode, targetNode, phase, worldName, cellX, cellZ, fencingToken, "", occurredAt);
    }
}
