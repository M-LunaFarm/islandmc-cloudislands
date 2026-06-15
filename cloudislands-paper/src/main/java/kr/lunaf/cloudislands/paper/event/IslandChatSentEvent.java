package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandChatSentEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID playerUuid;
    private final String channel;
    private final String message;

    public IslandChatSentEvent(UUID islandId, UUID playerUuid, String channel, String message) {
        super(true);
        this.islandId = islandId;
        this.playerUuid = playerUuid;
        this.channel = channel == null ? "" : channel;
        this.message = message == null ? "" : message;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String channel() {
        return channel;
    }

    public String message() {
        return message;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
