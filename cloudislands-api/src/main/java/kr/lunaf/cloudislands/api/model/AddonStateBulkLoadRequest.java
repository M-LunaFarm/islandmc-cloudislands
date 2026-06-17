package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record AddonStateBulkLoadRequest(
    String addonId,
    UUID islandId,
    String table
) {
    private static final String TABLE_STATE_KEY_PREFIX = "table/";

    public AddonStateBulkLoadRequest {
        addonId = addonId == null ? "" : addonId.trim();
        table = safeTableName(table);
    }

    public boolean islandScoped() {
        return islandId != null;
    }

    public static AddonStateBulkLoadRequest global(String addonId, String table) {
        return new AddonStateBulkLoadRequest(addonId, null, table);
    }

    public static AddonStateBulkLoadRequest island(String addonId, UUID islandId, String table) {
        return new AddonStateBulkLoadRequest(addonId, islandId, table);
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
