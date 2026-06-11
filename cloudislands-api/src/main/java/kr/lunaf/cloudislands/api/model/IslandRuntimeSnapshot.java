package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandRuntimeSnapshot(
    UUID islandId,
    IslandState state,
    String activeNode,
    String activeWorld,
    Integer cellX,
    Integer cellZ,
    String leaseOwner,
    long fencingToken,
    Instant activatedAt,
    Instant lastHeartbeat
) {}
