package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandFlagChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String flag;
    private final String value;
    private final Map<String, String> fields;

    public IslandFlagChangeEvent(UUID islandId, String flag, String value, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.flag = flag == null ? "" : flag;
        this.value = value == null ? "" : value;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String flag() {
        return flag;
    }

    public String value() {
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
