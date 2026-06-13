package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandMigratedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String fromNode;
    private final String toNode;
    private final String worldName;
    private final long fencingToken;
    private final Map<String, String> fields;

    public IslandMigratedEvent(UUID islandId, String fromNode, String toNode, String worldName, long fencingToken, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.fromNode = fromNode == null ? "" : fromNode;
        this.toNode = toNode == null ? "" : toNode;
        this.worldName = worldName == null ? "" : worldName;
        this.fencingToken = fencingToken;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String fromNode() {
        return fromNode;
    }

    public String toNode() {
        return toNode;
    }

    public String worldName() {
        return worldName;
    }

    public long fencingToken() {
        return fencingToken;
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
