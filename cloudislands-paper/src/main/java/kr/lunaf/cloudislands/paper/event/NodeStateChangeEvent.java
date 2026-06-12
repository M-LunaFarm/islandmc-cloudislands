package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class NodeStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String nodeId;
    private final String state;
    private final String operation;
    private final String reason;
    private final int recoveryRequired;
    private final Map<String, String> fields;

    public NodeStateChangeEvent(String nodeId, String state, String operation, String reason, int recoveryRequired, Map<String, String> fields) {
        super(true);
        this.nodeId = nodeId == null ? "" : nodeId;
        this.state = state == null ? "" : state;
        this.operation = operation == null ? "" : operation;
        this.reason = reason == null ? "" : reason;
        this.recoveryRequired = recoveryRequired;
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
}
