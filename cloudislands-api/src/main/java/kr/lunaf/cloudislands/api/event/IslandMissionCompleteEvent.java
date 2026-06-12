package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMissionCompleteEvent(UUID islandId, String missionKey, String kind, Instant occurredAt) implements CloudIslandEvent {}
