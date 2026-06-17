package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record AddonStateBulkLoadRequest(
    String addonId,
    UUID islandId,
    String table
) {
    public AddonStateBulkLoadRequest {
        addonId = addonId == null ? "" : addonId.trim();
        table = table == null ? "" : table.trim();
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
}
