package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandUpgradeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String upgradeKey;
    private final int level;
    private final Map<String, String> fields;

    public IslandUpgradeEvent(UUID islandId, String upgradeKey, int level, Map<String, String> fields) {
        super(true);
        this.islandId = islandId;
        this.upgradeKey = upgradeKey == null ? "" : upgradeKey;
        this.level = level;
        this.fields = Map.copyOf(fields);
    }

    public UUID islandId() {
        return islandId;
    }

    public String upgradeKey() {
        return upgradeKey;
    }

    public int level() {
        return level;
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
