package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRestoreRequestEvent(UUID islandId, String state, String targetNode, long snapshotNo, long fencingToken, Instant occurredAt) implements CloudIslandEvent {
    public IslandRestoreRequestEvent(UUID islandId, String state, String targetNode, long snapshotNo, Instant occurredAt) {
        this(islandId, state, targetNode, snapshotNo, 0L, occurredAt);
    }
}
