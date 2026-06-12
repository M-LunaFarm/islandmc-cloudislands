package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandMemberLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID playerUuid;
    private final Map<String, String> fields;

    public IslandMemberLeaveEvent(UUID islandId, UUID playerUuid, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.playerUuid = playerUuid;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID playerUuid() {
        return playerUuid;
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
