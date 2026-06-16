package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record AddonStateChangeEvent(String addonId, UUID islandId, String operation, String key, String table, int keys, int valueKeys, int tableKeys, int tables, Instant occurredAt) implements CloudGlobalEvent, CloudIslandEvent {
    public AddonStateChangeEvent(String addonId, UUID islandId, String operation, String key, String table, int keys, int tables, Instant occurredAt) {
        this(addonId, islandId, operation, key, table, keys, 0, 0, tables, occurredAt);
    }

    public int rootValueKeyCount() {
        return valueKeys;
    }

    public int tableKeyCount() {
        return tableKeys;
    }

    public int tableCount() {
        return tables;
    }

    public int totalStateKeyCount() {
        return keys;
    }

    public boolean bulkOperation() {
        return totalStateKeyCount() > 1 || tableCount() > 1;
    }

    public boolean tableOperation() {
        return tableCount() > 0 || tableKeyCount() > 0 || (table != null && !table.isBlank());
    }
}
