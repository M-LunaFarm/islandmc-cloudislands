package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandLimitChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String limitKey;
    private final long value;
    private final Map<String, String> fields;

    public IslandLimitChangeEvent(UUID islandId, String limitKey, long value, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.limitKey = limitKey == null ? "" : limitKey;
        this.value = value;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String limitKey() {
        return limitKey;
    }

    public long value() {
        return value;
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
