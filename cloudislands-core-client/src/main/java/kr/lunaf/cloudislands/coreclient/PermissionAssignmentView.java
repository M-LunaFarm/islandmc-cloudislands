package kr.lunaf.cloudislands.coreclient;

public record PermissionAssignmentView(String role, String playerUuid, String permission, boolean allowed, String version) {
    public PermissionAssignmentView {
        role = role == null ? "" : role;
        playerUuid = playerUuid == null ? "" : playerUuid;
        permission = permission == null ? "" : permission;
        version = version == null ? "" : version;
    }
}
