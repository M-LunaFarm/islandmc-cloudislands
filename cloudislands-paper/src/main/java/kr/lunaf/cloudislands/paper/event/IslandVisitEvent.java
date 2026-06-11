package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandVisitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID visitorUuid;
    private final Player visitor;
    private final String worldName;

    public IslandVisitEvent(UUID islandId, UUID visitorUuid, Player visitor, String worldName) {
        this.islandId = islandId;
        this.visitorUuid = visitorUuid;
        this.visitor = visitor;
        this.worldName = worldName;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID visitorUuid() {
        return visitorUuid;
    }

    public Player visitor() {
        return visitor;
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
