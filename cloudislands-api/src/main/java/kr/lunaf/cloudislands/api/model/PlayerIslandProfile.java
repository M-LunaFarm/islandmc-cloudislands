package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt, String locale, int disbandsRemaining, boolean islandFlyEnabled, boolean worldBorderEnabled, boolean blocksStackerEnabled, String borderColor) {
    public PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, "ko_kr");
    }

    public PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt, String locale) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, 0);
    }

    public PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt, String locale, int disbandsRemaining) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, disbandsRemaining, false);
    }

    public PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt, String locale, int disbandsRemaining, boolean islandFlyEnabled) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, disbandsRemaining, islandFlyEnabled, true, true);
    }

    public PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt, String locale, int disbandsRemaining, boolean islandFlyEnabled, boolean worldBorderEnabled, boolean blocksStackerEnabled) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, disbandsRemaining, islandFlyEnabled, worldBorderEnabled, blocksStackerEnabled, "blue");
    }

    public PlayerIslandProfile {
        locale = normalizeLocale(locale);
        disbandsRemaining = Math.max(0, disbandsRemaining);
        borderColor = normalizeBorderColor(borderColor);
    }

    public static String normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return "ko_kr";
        }
        String normalized = value.trim().replace('-', '_').toLowerCase(java.util.Locale.ROOT);
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }

    public static String normalizeBorderColor(String value) {
        return switch ((value == null ? "" : value.trim()).toLowerCase(java.util.Locale.ROOT)) {
            case "red", "빨강" -> "red";
            case "green", "초록" -> "green";
            default -> "blue";
        };
    }
}
