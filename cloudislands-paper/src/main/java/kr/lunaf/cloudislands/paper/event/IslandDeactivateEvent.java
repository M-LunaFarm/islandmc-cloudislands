package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandDeactivateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String nodeId;
    private final long snapshotNo;

    public IslandDeactivateEvent(UUID islandId, String nodeId, long snapshotNo) {
        super(true);
        this.islandId = islandId;
        this.nodeId = nodeId;
        this.snapshotNo = snapshotNo;
    }

    public UUID islandId() {
        return islandId;
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
