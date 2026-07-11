package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

/** A player with co-op access who is not part of the island's permanent team. */
public record IslandCoopSnapshot(UUID islandId, UUID playerUuid, Instant addedAt, Instant expiresAt) {
    public IslandCoopSnapshot {
        addedAt = addedAt == null ? Instant.EPOCH : addedAt;
    }

    public boolean temporary() {
        return expiresAt != null;
    }

    public boolean activeAt(Instant instant) {
        Instant checkedAt = instant == null ? Instant.now() : instant;
        return expiresAt == null || expiresAt.isAfter(checkedAt);
    }
}
