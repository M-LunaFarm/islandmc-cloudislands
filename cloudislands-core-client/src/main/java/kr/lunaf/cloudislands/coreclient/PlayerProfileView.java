package kr.lunaf.cloudislands.coreclient;

public record PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale, int disbandsRemaining, boolean islandFlyEnabled, boolean worldBorderEnabled, boolean blocksStackerEnabled, String borderColor) {
    public PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, 0);
    }

    public PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale, int disbandsRemaining) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, disbandsRemaining, false);
    }

    public PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale, int disbandsRemaining, boolean islandFlyEnabled) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, disbandsRemaining, islandFlyEnabled, true, true);
    }

    public PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale, int disbandsRemaining, boolean islandFlyEnabled, boolean worldBorderEnabled, boolean blocksStackerEnabled) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, disbandsRemaining, islandFlyEnabled, worldBorderEnabled, blocksStackerEnabled, "blue");
    }

    public PlayerProfileView {
        playerUuid = playerUuid == null ? "" : playerUuid;
        lastName = lastName == null ? "" : lastName;
        primaryIslandId = primaryIslandId == null ? "" : primaryIslandId;
        lastSeenAt = lastSeenAt == null ? "" : lastSeenAt;
        locale = locale == null ? "" : locale;
        disbandsRemaining = Math.max(0, disbandsRemaining);
        borderColor = kr.lunaf.cloudislands.api.model.PlayerIslandProfile.normalizeBorderColor(borderColor);
    }
}
