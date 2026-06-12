package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandPermissionChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String role;
    private final String permission;
    private final Boolean allowed;
    private final Map<String, String> fields;

    public IslandPermissionChangeEvent(UUID islandId, String role, String permission, Boolean allowed, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.role = role == null ? "" : role;
        this.permission = permission == null ? "" : permission;
        this.allowed = allowed;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String role() {
        return role;
    }

    public String permission() {
        return permission;
    }

    public Boolean allowed() {
        return allowed;
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
