package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMissionProgressEvent(UUID islandId, String missionKey, String kind, long progress, long goal, long amount, boolean completed, Instant occurredAt) implements CloudIslandEvent {}
