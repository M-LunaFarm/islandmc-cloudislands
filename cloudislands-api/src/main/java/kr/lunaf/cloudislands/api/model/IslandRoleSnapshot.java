package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandRoleSnapshot(
    UUID islandId,
    IslandRole role,
    int weight,
    String displayName
) {}
