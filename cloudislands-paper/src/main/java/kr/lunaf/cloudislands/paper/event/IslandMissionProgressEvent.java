package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandMissionProgressEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String missionKey;
    private final String kind;
    private final long progress;
    private final long goal;
    private final long amount;
    private final boolean completed;
    private final Map<String, String> fields;

    public IslandMissionProgressEvent(UUID islandId, String missionKey, String kind, long progress, long goal, long amount, boolean completed, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.missionKey = missionKey == null ? "" : missionKey;
        this.kind = kind == null ? "" : kind;
        this.progress = progress;
        this.goal = goal;
        this.amount = amount;
        this.completed = completed;
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

    public long progress() {
        return progress;
    }

    public long goal() {
        return goal;
    }

    public long amount() {
        return amount;
    }

    public boolean completed() {
        return completed;
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
