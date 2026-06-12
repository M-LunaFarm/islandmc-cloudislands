package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandResetEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final boolean requested;
    private final String state;
    private final String targetNode;
    private final String reason;
    private final Map<String, String> fields;

    public IslandResetEvent(UUID islandId, boolean requested, String state, String targetNode, String reason, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.requested = requested;
        this.state = state == null ? "" : state;
        this.targetNode = targetNode == null ? "" : targetNode;
        this.reason = reason == null ? "" : reason;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public boolean requested() {
        return requested;
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
