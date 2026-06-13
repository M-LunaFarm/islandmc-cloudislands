package kr.lunaf.cloudislands.api.model;

import java.time.Instant;

public record CloudIslandsAddonSnapshot(
    String id,
    String displayName,
    String version,
    boolean enabled,
    Instant registeredAt
) {}
