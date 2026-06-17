package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;
import java.util.UUID;

public interface AddonStateRepository {
    String TABLE_STATE_KEY_PREFIX = "table/";
    String TABLE_STATE_KEY_SHAPE = "table/{table}/{key}";
    int MAX_ADDON_ID_LENGTH = 128;
    int MAX_KEY_LENGTH = 128;
    int MAX_VALUE_LENGTH = 65535;
    int MAX_KEYS_PER_ADDON = 4096;

    Map<String, Integer> globalStateCounts();
    Map<String, Integer> islandStateCounts();
    Map<String, String> list(String addonId);
    default Map<String, String> table(String addonId, String table) {
        return tableValues(list(addonId), table);
    }
    default Map<String, String> tableState(String addonId, String table) {
        return table(addonId, table);
    }
    default Map<String, String> tableKeyValueBulkLoad(String addonId, String table) {
        return table(addonId, table);
    }
    default Map<String, String> bulkLoadTableKeyValue(String addonId, String table) {
        return tableKeyValueBulkLoad(addonId, table);
    }
    Map<String, String> put(String addonId, String key, String value);
    Map<String, String> put(String addonId, Map<String, String> values);
    default Map<String, String> bulkSave(String addonId, Map<String, String> values) {
        return put(addonId, values);
    }
    default Map<String, String> tableKeyValueBulkSave(String addonId, Map<String, String> values) {
        return bulkSave(addonId, values);
    }
    default Map<String, String> tableKeyValueBulkSave(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        java.util.HashMap<String, String> merged = new java.util.HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    merged.put(safeKey(key), safeValue(value));
                }
            });
        }
        merged.putAll(tableStateValues(tables));
        return tableKeyValueBulkSave(addonId, Map.copyOf(merged));
    }
    default Map<String, String> tableBulk(String addonId, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSave(addonId, Map.of(), tables);
    }
    default Map<String, String> bulkTable(String addonId, Map<String, Map<String, String>> tables) {
        return tableBulk(addonId, tables);
    }
    Map<String, String> remove(String addonId, String key);
    Map<String, String> removePrefix(String addonId, String keyPrefix);
    Map<String, String> replacePrefix(String addonId, String keyPrefix, Map<String, String> values);
    void clear(String addonId);
    Map<String, String> listIsland(String addonId, UUID islandId);
    default Map<String, String> tableIsland(String addonId, UUID islandId, String table) {
        return tableValues(listIsland(addonId, islandId), table);
    }
    default Map<String, String> tableStateIsland(String addonId, UUID islandId, String table) {
        return tableIsland(addonId, islandId, table);
    }
    default Map<String, String> tableKeyValueBulkLoadIsland(String addonId, UUID islandId, String table) {
        return tableIsland(addonId, islandId, table);
    }
    default Map<String, String> bulkLoadTableKeyValueIsland(String addonId, UUID islandId, String table) {
        return tableKeyValueBulkLoadIsland(addonId, islandId, table);
    }
    Map<String, String> putIsland(String addonId, UUID islandId, String key, String value);
    Map<String, String> putIsland(String addonId, UUID islandId, Map<String, String> values);
    default Map<String, String> bulkSaveIsland(String addonId, UUID islandId, Map<String, String> values) {
        return putIsland(addonId, islandId, values);
    }
    default Map<String, String> tableKeyValueBulkSaveIsland(String addonId, UUID islandId, Map<String, String> values) {
        return bulkSaveIsland(addonId, islandId, values);
    }
    default Map<String, String> tableKeyValueBulkSaveIsland(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        java.util.HashMap<String, String> merged = new java.util.HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    merged.put(safeKey(key), safeValue(value));
                }
            });
        }
        merged.putAll(tableStateValues(tables));
        return tableKeyValueBulkSaveIsland(addonId, islandId, Map.copyOf(merged));
    }
    default Map<String, String> tableBulkIsland(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveIsland(addonId, islandId, Map.of(), tables);
    }
    default Map<String, String> bulkTableIsland(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableBulkIsland(addonId, islandId, tables);
    }
    Map<String, String> removeIsland(String addonId, UUID islandId, String key);
    Map<String, String> removeIslandPrefix(String addonId, UUID islandId, String keyPrefix);
    Map<String, String> replaceIslandPrefix(String addonId, UUID islandId, String keyPrefix, Map<String, String> values);
    void clearIsland(String addonId, UUID islandId);

    static String safeAddonId(String addonId) {
        String value = addonId == null ? "" : addonId.trim();
        if (value.isBlank()) {
            value = "unknown-addon";
        }
        if (value.length() > MAX_ADDON_ID_LENGTH) {
            throw new IllegalArgumentException("Addon id is too long");
        }
        return value;
    }

    static String safeKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state key is required");
        }
        if (value.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Addon state key is too long");
        }
        return value;
    }

    static String safeValue(String value) {
        String safe = value == null ? "" : value;
        if (safe.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Addon state value is too long");
        }
        return safe;
    }

    static UUID safeIslandId(UUID islandId) {
        if (islandId == null || islandId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Island id is required");
        }
        return islandId;
    }

    static Map<String, String> tableStateValues(Map<String, Map<String, String>> tables) {
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        if (tables != null) {
            tables.forEach((table, values) -> state.putAll(tableStateValues(table, values)));
        }
        return Map.copyOf(state);
    }

    static Map<String, String> tableStateValues(String table, Map<String, String> values) {
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        String safeTable = safeTableNameOrBlank(table);
        if (safeTable.isBlank()) {
            return Map.of();
        }
        values.forEach((key, value) -> {
            String safeKey = safeTableKeyOrBlank(key);
            if (!safeKey.isBlank() && tableStateKeyLength(safeTable, safeKey) <= MAX_KEY_LENGTH) {
                state.put(tableStateKey(safeTable, safeKey), safeValue(value));
            }
        });
        return Map.copyOf(state);
    }

    static Map<String, String> tableValues(Map<String, String> state, String table) {
        if (state == null || state.isEmpty()) {
            return Map.of();
        }
        String prefix = tableStateKeyPrefix(table);
        java.util.HashMap<String, String> values = new java.util.HashMap<>();
        state.forEach((key, value) -> {
            if (key != null && value != null && key.startsWith(prefix) && key.length() > prefix.length()) {
                values.put(key.substring(prefix.length()), value);
            }
        });
        return Map.copyOf(values);
    }

    static String tableStateKey(String table, String key) {
        return TABLE_STATE_KEY_PREFIX + safeTableName(table) + "/" + safeKey(key);
    }

    static String tableStateKeyPrefix(String table) {
        return TABLE_STATE_KEY_PREFIX + safeTableName(table) + "/";
    }

    static String safeTableName(String table) {
        String value = safeKey(table);
        if (value.contains("/")) {
            throw new IllegalArgumentException("Addon state table cannot contain /");
        }
        return value;
    }

    static String safeTableNameOrBlank(String table) {
        String value = table == null ? "" : table.trim();
        if (value.startsWith(TABLE_STATE_KEY_PREFIX)) {
            value = value.substring(TABLE_STATE_KEY_PREFIX.length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank() || value.contains("/") || value.length() > MAX_KEY_LENGTH) {
            return "";
        }
        return value;
    }

    static String safeTableKeyOrBlank(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isBlank()) {
            return "";
        }
        return value;
    }

    static int tableStateKeyLength(String table, String key) {
        return TABLE_STATE_KEY_PREFIX.length() + table.length() + 1 + key.length();
    }

    static void requireKeyCapacity(Map<String, String> state, String key) {
        if (state != null && !state.containsKey(key) && state.size() >= MAX_KEYS_PER_ADDON) {
            throw new IllegalArgumentException("Addon state key limit reached");
        }
    }
}
