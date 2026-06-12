package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandBiomeChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String biomeKey;
    private final Map<String, String> fields;

    public IslandBiomeChangeEvent(UUID islandId, String biomeKey, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.biomeKey = biomeKey == null ? "" : biomeKey;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String biomeKey() {
        return biomeKey;
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
