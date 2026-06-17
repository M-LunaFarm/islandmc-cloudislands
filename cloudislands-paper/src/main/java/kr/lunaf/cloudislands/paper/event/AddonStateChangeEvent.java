package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class AddonStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String addonId;
    private final UUID islandId;
    private final String operation;
    private final String key;
    private final String table;
    private final int keys;
    private final int valueKeys;
    private final int tableKeys;
    private final int tables;
    private final Map<String, String> fields;

    public AddonStateChangeEvent(String addonId, UUID islandId, String operation, String key, String table, int keys, int valueKeys, int tableKeys, int tables, Map<String, String> fields) {
        super(true);
        this.addonId = addonId == null ? "" : addonId;
        this.islandId = islandId;
        this.operation = operation == null ? "" : operation;
        this.key = key == null ? "" : key;
        this.table = table == null ? "" : table;
        this.keys = keys;
        this.valueKeys = valueKeys;
        this.tableKeys = tableKeys;
        this.tables = tables;
        this.fields = Map.copyOf(fields);
    }

    public String addonId() {
        return addonId;
    }

    public UUID islandId() {
        return islandId;
    }

    public String operation() {
        return operation;
    }

    public String key() {
        return key;
    }

    public String table() {
        return table;
    }

    public int keys() {
        return keys;
    }

    public int valueKeys() {
        return valueKeys;
    }

    public int tableKeys() {
        return tableKeys;
    }

    public int tables() {
        return tables;
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
