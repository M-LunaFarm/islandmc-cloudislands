package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public record NodeStateChangedEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, Instant occurredAt) implements CloudGlobalEvent {}
