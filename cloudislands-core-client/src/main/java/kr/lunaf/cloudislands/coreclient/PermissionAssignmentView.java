package kr.lunaf.cloudislands.coreclient;

public record PermissionAssignmentView(String role, String playerUuid, String permission, boolean allowed, String version, String playerName) {
    public PermissionAssignmentView(String role, String playerUuid, String permission, boolean allowed, String version) {
        this(role, playerUuid, permission, allowed, version, "");
    }

    public PermissionAssignmentView {
        role = role == null ? "" : role;
        playerUuid = playerUuid == null ? "" : playerUuid;
        permission = permission == null ? "" : permission;
        version = version == null ? "" : version;
        playerName = playerName == null ? "" : playerName;
    }
}
