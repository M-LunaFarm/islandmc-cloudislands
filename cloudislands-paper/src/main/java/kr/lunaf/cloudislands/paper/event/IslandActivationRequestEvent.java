package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandActivationRequestEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String state;
    private final String targetNode;
    private final Map<String, String> fields;

    public IslandActivationRequestEvent(UUID islandId, String state, String targetNode, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.state = state == null ? "" : state;
        this.targetNode = targetNode == null ? "" : targetNode;
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
