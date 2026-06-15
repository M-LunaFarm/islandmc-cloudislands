package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandOwnershipChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID actorUuid;
    private final UUID targetUuid;

    public IslandOwnershipChangeEvent(UUID islandId, UUID actorUuid, UUID targetUuid) {
        super(true);
        this.islandId = islandId;
        this.actorUuid = actorUuid;
        this.targetUuid = targetUuid;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID actorUuid() {
        return actorUuid;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
