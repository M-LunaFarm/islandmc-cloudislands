package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class NodeStateChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String nodeId;
    private final String state;
    private final String operation;
    private final String reason;
    private final int recoveryRequired;
    private final int clearedSessions;
    private final int clearedTickets;
    private final Map<String, String> fields;

    public NodeStateChangedEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, Map<String, String> fields) {
        this(nodeId, state, operation, reason, recoveryRequired, count(fields, "clearedSessions"), count(fields, "clearedTickets"), fields);
    }

    public NodeStateChangedEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, int clearedSessions, int clearedTickets, Map<String, String> fields) {
        super(true);
        this.nodeId = nodeId == null ? "" : nodeId;
        this.state = state == null ? "" : state;
        this.operation = operation == null ? "" : operation;
        this.reason = reason == null ? "" : reason;
        this.recoveryRequired = recoveryRequired;
        this.clearedSessions = Math.max(0, clearedSessions);
        this.clearedTickets = Math.max(0, clearedTickets);
        this.fields = Map.copyOf(fields);
    }

    public String nodeId() {
        return nodeId;
    }

    public String state() {
        return state;
    }

    public String operation() {
        return operation;
    }

    public String reason() {
        return reason;
    }

    public int recoveryRequired() {
        return recoveryRequired;
    }

    public int clearedSessions() {
        return clearedSessions;
    }

    public int clearedTickets() {
        return clearedTickets;
    }

    public Map<String, String> fields() {
        return fields;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static int count(Map<String, String> fields, String key) {
        if (fields == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(fields.getOrDefault(key, "0")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
