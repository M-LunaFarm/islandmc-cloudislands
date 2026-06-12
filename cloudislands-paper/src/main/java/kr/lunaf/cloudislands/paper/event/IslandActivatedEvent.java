package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandActivatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String nodeId;
    private final int readyTickets;
    private final Map<String, String> fields;

    public IslandActivatedEvent(UUID islandId, String nodeId, int readyTickets, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.nodeId = nodeId == null ? "" : nodeId;
        this.readyTickets = readyTickets;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String nodeId() {
        return nodeId;
    }

    public int readyTickets() {
        return readyTickets;
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
