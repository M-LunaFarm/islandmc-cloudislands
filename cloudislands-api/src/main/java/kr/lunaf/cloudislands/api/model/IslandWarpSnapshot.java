package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandWarpSnapshot(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, Instant createdAt) {}
