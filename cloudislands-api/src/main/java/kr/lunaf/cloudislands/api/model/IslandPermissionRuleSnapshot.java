package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandPermissionRuleSnapshot(
    UUID islandId,
    IslandRole role,
    IslandPermission permission,
    boolean allowed
) {}
