package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeleteBackupFailedEvent(UUID islandId, String reason, String error, Instant occurredAt) implements CloudIslandEvent {}
