package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandAccessChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final Boolean publicAccess;
    private final Boolean locked;
    private final Map<String, String> fields;

    public IslandAccessChangeEvent(UUID islandId, Boolean publicAccess, Boolean locked, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.publicAccess = publicAccess;
        this.locked = locked;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public Boolean publicAccess() {
        return publicAccess;
    }

    public Boolean locked() {
        return locked;
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
