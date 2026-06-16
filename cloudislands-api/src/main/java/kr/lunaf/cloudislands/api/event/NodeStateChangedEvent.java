package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public record NodeStateChangedEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, int clearedSessions, int clearedTickets, Instant occurredAt) implements CloudGlobalEvent {
    public NodeStateChangedEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, Instant occurredAt) {
        this(nodeId, state, operation, reason, recoveryRequired, 0, 0, occurredAt);
    }
}
