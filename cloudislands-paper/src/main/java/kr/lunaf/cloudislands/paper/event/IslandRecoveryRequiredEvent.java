package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandRecoveryRequiredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String nodeId;
    private final String reason;
    private final Map<String, String> fields;

    public IslandRecoveryRequiredEvent(UUID islandId, String nodeId, String reason, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.nodeId = nodeId == null ? "" : nodeId;
        this.reason = reason == null ? "" : reason;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String nodeId() {
        return nodeId;
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
