package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandRenamedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID actorUuid;
    private final String name;
    private final Map<String, String> fields;

    public IslandRenamedEvent(UUID islandId, UUID actorUuid, String name, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.actorUuid = actorUuid;
        this.name = name == null ? "" : name;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID actorUuid() {
        return actorUuid;
    }

    public String name() {
        return name;
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
