package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandRuntimeChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String state;
    private final String targetNode;
    private final String reason;
    private final String error;
    private final Map<String, String> fields;

    public IslandRuntimeChangeEvent(UUID islandId, String state, String targetNode, String reason, String error, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.state = state == null ? "" : state;
        this.targetNode = targetNode == null ? "" : targetNode;
        this.reason = reason == null ? "" : reason;
        this.error = error == null ? "" : error;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String state() {
        return state;
    }

    public String targetNode() {
        return targetNode;
    }

    public String reason() {
        return reason;
    }

    public String error() {
        return error;
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
