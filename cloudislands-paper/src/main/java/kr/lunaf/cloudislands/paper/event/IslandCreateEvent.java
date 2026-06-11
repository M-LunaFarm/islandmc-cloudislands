package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandCreateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID jobId;
    private final String nodeId;
    private final String worldName;

    public IslandCreateEvent(UUID islandId, UUID jobId, String nodeId, String worldName) {
        this.islandId = islandId;
        this.jobId = jobId;
        this.nodeId = nodeId;
        this.worldName = worldName;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID jobId() {
        return jobId;
    }

    public String nodeId() {
        return nodeId;
    }

    public String worldName() {
        return worldName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
