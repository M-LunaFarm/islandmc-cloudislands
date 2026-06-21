package kr.lunaf.cloudislands.coreclient;

public record PlayerProfileView(String playerUuid, String lastName, String primaryIslandId, String lastSeenAt, String locale) {
    public PlayerProfileView {
        playerUuid = playerUuid == null ? "" : playerUuid;
        lastName = lastName == null ? "" : lastName;
        primaryIslandId = primaryIslandId == null ? "" : primaryIslandId;
        lastSeenAt = lastSeenAt == null ? "" : lastSeenAt;
        locale = locale == null ? "" : locale;
    }
}
