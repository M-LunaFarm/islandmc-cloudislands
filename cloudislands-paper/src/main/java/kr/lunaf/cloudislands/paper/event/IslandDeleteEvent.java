package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandDeleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID jobId;
    private final String nodeId;
    private final long snapshotNo;

    public IslandDeleteEvent(UUID islandId, UUID jobId, String nodeId, long snapshotNo) {
        this.islandId = islandId;
        this.jobId = jobId;
        this.nodeId = nodeId;
        this.snapshotNo = snapshotNo;
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

    public long snapshotNo() {
        return snapshotNo;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
