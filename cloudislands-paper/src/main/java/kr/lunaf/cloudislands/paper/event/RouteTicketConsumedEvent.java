package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RouteTicketConsumedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID ticketId;
    private final UUID playerUuid;
    private final Player player;
    private final RouteAction action;
    private final String worldName;

    public RouteTicketConsumedEvent(UUID islandId, UUID ticketId, UUID playerUuid, Player player, RouteAction action, String worldName) {
        this.islandId = islandId;
        this.ticketId = ticketId;
        this.playerUuid = playerUuid;
        this.player = player;
        this.action = action;
        this.worldName = worldName;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public Player player() {
        return player;
    }

    public RouteAction action() {
        return action;
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
