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
    public AddonStateBulkSaveRequest {
        addonId = addonId == null ? "" : addonId.trim();
        table = table == null ? "" : table.trim();
        values = copyValues(values);
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

    private static Map<String, String> copyValues(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }

    private static Map<String, Map<String, String>> copyTables(Map<String, Map<String, String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, String>> copy = new LinkedHashMap<>();
        source.forEach((table, values) -> {
            if (table != null && !table.isBlank()) {
                copy.put(table.trim(), copyValues(values));
            }
        });
        return Map.copyOf(copy);
    }
}
