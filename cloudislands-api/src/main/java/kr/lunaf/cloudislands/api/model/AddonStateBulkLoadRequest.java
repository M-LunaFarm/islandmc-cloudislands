package kr.lunaf.cloudislands.api.model;

import java.util.List;
import java.util.UUID;

public record AddonStateBulkLoadRequest(
    String addonId,
    UUID islandId,
    String table
) {
    public static final String API_NAME = "table-key-value-bulk-load";
    public static final String TABLE_FIELD = "table";
    public static final String ISLAND_SCOPE_FIELD = "islandId";
    public static final String WIRE_SHAPE = "addonId,islandId?,table";
    public static final String GLOBAL_ENDPOINT = "/v1/addons/state/table/key-value/bulk-load";
    public static final String ISLAND_ENDPOINT = "/v1/addons/islands/state/table/key-value/bulk-load";
    public static final String GLOBAL_TABLE_LOAD_ALIAS = "/v1/addons/state/table/load";
    public static final String ISLAND_TABLE_LOAD_ALIAS = "/v1/addons/islands/state/table/load";
    public static final List<String> GLOBAL_ENDPOINTS = List.of(GLOBAL_ENDPOINT, GLOBAL_TABLE_LOAD_ALIAS);
    public static final List<String> ISLAND_ENDPOINTS = List.of(ISLAND_ENDPOINT, ISLAND_TABLE_LOAD_ALIAS);
    private static final String TABLE_STATE_KEY_PREFIX = "table/";

    public AddonStateBulkLoadRequest {
        addonId = addonId == null ? "" : addonId.trim();
        table = safeTableName(table);
    }

    public boolean islandScoped() {
        return islandId != null && !islandId.equals(new UUID(0L, 0L));
    }

    public String apiName() {
        return API_NAME;
    }

    public String scopeName() {
        return islandScoped() ? "island" : "global";
    }

    public static AddonStateBulkLoadRequest global(String addonId, String table) {
        return new AddonStateBulkLoadRequest(addonId, null, table);
    }

    public static AddonStateBulkLoadRequest globalBulk(String addonId, String table) {
        return global(addonId, table);
    }

    public static AddonStateBulkLoadRequest globalTableKeyValueBulkLoad(String addonId, String table) {
        return global(addonId, table);
    }

    public static AddonStateBulkLoadRequest tableKeyValueBulkLoad(String addonId, String table) {
        return global(addonId, table);
    }

    public static AddonStateBulkLoadRequest island(String addonId, UUID islandId, String table) {
        return new AddonStateBulkLoadRequest(addonId, islandId, table);
    }

    public static AddonStateBulkLoadRequest islandBulk(String addonId, UUID islandId, String table) {
        return island(addonId, islandId, table);
    }

    public static AddonStateBulkLoadRequest islandTableKeyValueBulkLoad(String addonId, UUID islandId, String table) {
        return island(addonId, islandId, table);
    }

    public static AddonStateBulkLoadRequest tableKeyValueBulkLoad(String addonId, UUID islandId, String table) {
        return island(addonId, islandId, table);
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
        if (value.contains("/")) {
            return "";
        }
        return value;
    }
}
