package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandRoleCatalogChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String role;
    private final String operation;
    private final Map<String, String> fields;

    public IslandRoleCatalogChangeEvent(UUID islandId, String role, String operation, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.role = role == null ? "" : role;
        this.operation = operation == null ? "" : operation;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String role() {
        return role;
    }

    public String operation() {
        return operation;
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
