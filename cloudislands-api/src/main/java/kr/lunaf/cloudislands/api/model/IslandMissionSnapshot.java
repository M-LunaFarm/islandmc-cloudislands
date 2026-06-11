package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandMissionSnapshot(
    UUID islandId,
    String missionKey,
    String kind,
    String title,
    long progress,
    long goal,
    boolean completed,
    String reward,
    Instant updatedAt
) {}
