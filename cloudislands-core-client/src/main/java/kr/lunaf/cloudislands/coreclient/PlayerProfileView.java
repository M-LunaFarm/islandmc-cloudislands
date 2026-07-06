package kr.lunaf.cloudislands.coreclient;

public record PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale, int disbandsRemaining) {
    public PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale) {
        this(playerUuid, lastName, primaryIslandId, lastSeenAt, locale, 0);
    }

    public PlayerProfileView {
        playerUuid = playerUuid == null ? "" : playerUuid;
        lastName = lastName == null ? "" : lastName;
        primaryIslandId = primaryIslandId == null ? "" : primaryIslandId;
        lastSeenAt = lastSeenAt == null ? "" : lastSeenAt;
        locale = locale == null ? "" : locale;
        disbandsRemaining = Math.max(0, disbandsRemaining);
    }
}
