package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandWarpDeleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String warpName;
    private final Map<String, String> fields;

    public IslandWarpDeleteEvent(UUID islandId, String warpName, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.warpName = warpName == null ? "" : warpName;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String warpName() {
        return warpName;
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
