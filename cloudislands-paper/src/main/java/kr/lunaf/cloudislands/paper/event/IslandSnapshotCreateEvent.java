package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandSnapshotCreateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final long snapshotNo;
    private final String reason;
    private final Map<String, String> fields;

    public IslandSnapshotCreateEvent(UUID islandId, long snapshotNo, String reason, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.snapshotNo = snapshotNo;
        this.reason = reason == null ? "" : reason;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public long snapshotNo() {
        return snapshotNo;
    }

    public String reason() {
        return reason;
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
