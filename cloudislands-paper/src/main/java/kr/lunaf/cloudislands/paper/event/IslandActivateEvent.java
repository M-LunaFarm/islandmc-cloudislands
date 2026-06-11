package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandActivateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String nodeId;
    private final String worldName;
    private final int cellX;
    private final int cellZ;
    private final long schemaVersion;

    public IslandActivateEvent(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long schemaVersion) {
        super(true);
        this.islandId = islandId;
        this.nodeId = nodeId;
        this.worldName = worldName;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.schemaVersion = schemaVersion;
    }

    public UUID islandId() {
        return islandId;
    }

    public String nodeId() {
        return nodeId;
    }

    public String worldName() {
        return worldName;
    }

    public int cellX() {
        return cellX;
    }

    public int cellZ() {
        return cellZ;
    }

    public long schemaVersion() {
        return schemaVersion;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
