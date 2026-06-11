package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandPreActivateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID jobId;
    private final IslandJobType jobType;
    private final String nodeId;
    private boolean cancelled;

    public IslandPreActivateEvent(UUID islandId, UUID jobId, IslandJobType jobType, String nodeId) {
        super(true);
        this.islandId = islandId;
        this.jobId = jobId;
        this.jobType = jobType;
        this.nodeId = nodeId;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID jobId() {
        return jobId;
    }

    public IslandJobType jobType() {
        return jobType;
    }

    public String nodeId() {
        return nodeId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
