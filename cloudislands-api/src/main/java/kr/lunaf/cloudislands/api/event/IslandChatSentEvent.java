package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandChatSentEvent(UUID islandId, UUID playerUuid, String channel, String message, Instant occurredAt) implements CloudIslandEvent {}
