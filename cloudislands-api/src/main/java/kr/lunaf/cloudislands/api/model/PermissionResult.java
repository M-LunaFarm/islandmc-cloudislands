package kr.lunaf.cloudislands.api.model;

public record PermissionResult(boolean allowed, String reason, IslandRole effectiveRole) {
    public static PermissionResult allow(IslandRole role) {
        return new PermissionResult(true, "ALLOW", role);
    }

    public static PermissionResult deny(String reason, IslandRole role) {
        return new PermissionResult(false, reason, role);
    }
}
