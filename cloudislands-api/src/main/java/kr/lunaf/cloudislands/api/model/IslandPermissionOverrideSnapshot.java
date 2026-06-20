package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandPermissionOverrideSnapshot(
    UUID islandId,
    UUID playerUuid,
    IslandPermission permission,
    boolean allowed
) {}
