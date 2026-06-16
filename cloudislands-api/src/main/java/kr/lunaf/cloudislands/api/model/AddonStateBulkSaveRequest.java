package kr.lunaf.cloudislands.api.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AddonStateBulkSaveRequest(
    String addonId,
    UUID islandId,
    String table,
    Map<String, String> values,
    Map<String, Map<String, String>> tables
) {
    private static final String TABLE_STATE_KEY_PREFIX = "table/";
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_VALUE_LENGTH = 65535;

    public AddonStateBulkSaveRequest {
        addonId = addonId == null ? "" : addonId.trim();
        table = safeTableName(table);
        values = table.isBlank() ? copyValues(values) : copyTableValues(table, values);
        tables = copyTables(tables);
    }

    public static AddonStateBulkSaveRequest global(String addonId, Map<String, String> values) {
        return new AddonStateBulkSaveRequest(addonId, null, "", values, Map.of());
    }

    public static AddonStateBulkSaveRequest globalTables(String addonId, Map<String, Map<String, String>> tables) {
        return new AddonStateBulkSaveRequest(addonId, null, "", Map.of(), tables);
    }

    public static AddonStateBulkSaveRequest globalTable(String addonId, String table, Map<String, String> values) {
        return new AddonStateBulkSaveRequest(addonId, null, table, values, Map.of());
    }

    public static AddonStateBulkSaveRequest island(String addonId, UUID islandId, Map<String, String> values) {
        return new AddonStateBulkSaveRequest(addonId, islandId, "", values, Map.of());
    }

    public static AddonStateBulkSaveRequest islandTables(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return new AddonStateBulkSaveRequest(addonId, islandId, "", Map.of(), tables);
    }

    public static AddonStateBulkSaveRequest islandTable(String addonId, UUID islandId, String table, Map<String, String> values) {
        return new AddonStateBulkSaveRequest(addonId, islandId, table, values, Map.of());
    }

    public boolean islandScoped() {
        return islandId != null && !islandId.equals(new UUID(0L, 0L));
    }

    public boolean tableScoped() {
        return !table.isBlank();
    }

    public int rootValueKeyCount() {
        return tableScoped() ? 0 : values.size();
    }

    public int tableKeyCount() {
        int count = tableScoped() ? values.size() : 0;
        for (Map<String, String> tableValues : tables.values()) {
            count += tableValues.size();
        }
        return count;
    }

    public int tableCount() {
        int count = tables.size();
        if (tableScoped() && !values.isEmpty()) {
            count++;
        }
        return count;
    }

    public int totalStateKeyCount() {
        return rootValueKeyCount() + tableKeyCount();
    }

    private static Map<String, String> copyValues(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String safeKey = safeRootKey(key);
            if (!safeKey.isBlank()) {
                copy.put(safeKey, safeValue(value));
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<String, String> copyTableValues(String table, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String safeKey = safeTableKey(key);
            if (!safeKey.isBlank() && tableKeyLength(table, safeKey) <= MAX_KEY_LENGTH) {
                copy.put(safeKey, safeValue(value));
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<String, Map<String, String>> copyTables(Map<String, Map<String, String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, String>> copy = new LinkedHashMap<>();
        source.forEach((table, values) -> {
            String safeTable = safeTableName(table);
            if (!safeTable.isBlank()) {
                copy.put(safeTable, copyTableValues(safeTable, values));
            }
        });
        return Map.copyOf(copy);
    }

    private static String safeRootKey(String key) {
        String value = key == null ? "" : key.trim();
        return value.length() > MAX_KEY_LENGTH ? value.substring(0, MAX_KEY_LENGTH) : value;
    }

    private static String safeTableKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.contains("/")) {
            return "";
        }
        return value;
    }

    private static String safeTableName(String table) {
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
        return value;
    }

    private static int tableKeyLength(String table, String key) {
        return TABLE_STATE_KEY_PREFIX.length() + table.length() + 1 + key.length();
    }

    private static String safeValue(String value) {
        String safe = value == null ? "" : value;
        return safe.length() > MAX_VALUE_LENGTH ? safe.substring(0, MAX_VALUE_LENGTH) : safe;
    }
}
