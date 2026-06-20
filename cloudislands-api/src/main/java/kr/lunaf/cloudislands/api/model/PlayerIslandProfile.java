package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt, String locale) {
    public PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, "ko_kr");
    }

    public PlayerIslandProfile {
        locale = normalizeLocale(locale);
    }

    public static String normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return "ko_kr";
        }
        String normalized = value.trim().replace('-', '_').toLowerCase(java.util.Locale.ROOT);
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }
}
