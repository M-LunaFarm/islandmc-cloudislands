package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandMemberChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID playerUuid;
    private final String action;
    private final String oldRole;
    private final String newRole;
    private final Map<String, String> fields;

    public IslandMemberChangedEvent(UUID islandId, UUID playerUuid, String action, String oldRole, String newRole, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.playerUuid = playerUuid;
        this.action = action == null ? "" : action;
        this.oldRole = oldRole == null ? "" : oldRole;
        this.newRole = newRole == null ? "" : newRole;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String action() {
        return action;
    }

    public String oldRole() {
        return oldRole;
    }

    public String newRole() {
        return newRole;
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
