package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandWarpSnapshot(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, Instant createdAt, String category) {
    public IslandWarpSnapshot(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, Instant createdAt) {
        this(islandId, name, location, publicAccess, createdBy, createdAt, "default");
    }

    public IslandWarpSnapshot {
        category = normalizeCategory(category);
    }

    public static String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        String normalized = value.trim().replace(' ', '-').toLowerCase(java.util.Locale.ROOT);
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }
}
