package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandDeletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final long snapshotNo;
    private final Map<String, String> fields;

    public IslandDeletedEvent(UUID islandId, long snapshotNo, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.snapshotNo = snapshotNo;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public long snapshotNo() {
        return snapshotNo;
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
