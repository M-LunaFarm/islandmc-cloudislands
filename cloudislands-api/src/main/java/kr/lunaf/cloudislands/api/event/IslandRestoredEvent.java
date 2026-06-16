package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRestoredEvent(UUID islandId, long snapshotNo, String state, String targetNode, String worldName, int cellX, int cellZ, long fencingToken, String placementSource, Instant occurredAt) implements CloudIslandEvent {
    public IslandRestoredEvent(UUID islandId, long snapshotNo, String state, Instant occurredAt) {
        this(islandId, snapshotNo, state, "", "", 0, 0, 0L, "", occurredAt);
    }
}
