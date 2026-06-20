package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt) {
    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt) {
        this(islandId, playerUuid, role, joinedAt, null);
    }
}
