package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandMigrationEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final boolean requested;
    private final String targetNode;
    private final String phase;
    private final String worldName;
    private final long fencingToken;
    private final Map<String, String> fields;

    public IslandMigrationEvent(UUID islandId, boolean requested, String targetNode, String phase, String worldName, long fencingToken, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.requested = requested;
        this.targetNode = targetNode == null ? "" : targetNode;
        this.phase = phase == null ? "" : phase;
        this.worldName = worldName == null ? "" : worldName;
        this.fencingToken = fencingToken;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public boolean requested() {
        return requested;
    }

    public String targetNode() {
        return targetNode;
    }

    public String phase() {
        return phase;
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
