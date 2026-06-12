package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandMissionCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String missionKey;
    private final String kind;
    private final Map<String, String> fields;

    public IslandMissionCompleteEvent(UUID islandId, String missionKey, String kind, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.missionKey = missionKey == null ? "" : missionKey;
        this.kind = kind == null ? "" : kind;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String missionKey() {
        return missionKey;
    }

    public String kind() {
        return kind;
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
