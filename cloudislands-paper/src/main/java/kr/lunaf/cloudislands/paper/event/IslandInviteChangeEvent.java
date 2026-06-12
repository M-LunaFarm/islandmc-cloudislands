package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandInviteChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID inviteId;
    private final UUID playerUuid;
    private final UUID targetUuid;
    private final String state;
    private final Boolean accepted;
    private final Boolean declined;
    private final Map<String, String> fields;

    public IslandInviteChangeEvent(UUID islandId, UUID inviteId, UUID playerUuid, UUID targetUuid, String state, Boolean accepted, Boolean declined, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.inviteId = inviteId;
        this.playerUuid = playerUuid;
        this.targetUuid = targetUuid;
        this.state = state == null ? "" : state;
        this.accepted = accepted;
        this.declined = declined;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID inviteId() {
        return inviteId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String state() {
        return state;
    }

    public Boolean accepted() {
        return accepted;
    }

    public Boolean declined() {
        return declined;
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
