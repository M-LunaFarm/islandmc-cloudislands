package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public record NodeStateChangeEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, Instant occurredAt) implements CloudGlobalEvent {}
