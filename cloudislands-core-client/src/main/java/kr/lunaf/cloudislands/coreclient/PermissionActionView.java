package kr.lunaf.cloudislands.coreclient;

public record PermissionActionView(boolean accepted, String code, String playerUuid, String playerName) {
    public PermissionActionView(boolean accepted, String code) {
        this(accepted, code, "", "");
    }

    public PermissionActionView {
        code = code == null ? "" : code;
        playerUuid = playerUuid == null ? "" : playerUuid;
        playerName = playerName == null ? "" : playerName;
    }
}
