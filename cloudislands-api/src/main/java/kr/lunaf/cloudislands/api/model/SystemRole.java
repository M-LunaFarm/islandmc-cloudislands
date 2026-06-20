package kr.lunaf.cloudislands.api.model;

public enum SystemRole {
    OWNER,
    VISITOR,
    BANNED;

    public RoleId roleId() {
        return RoleId.of(name());
    }

    public static boolean isSystemRole(RoleId roleId) {
        return from(roleId) != null;
    }

    public static SystemRole from(RoleId roleId) {
        if (roleId == null) {
            return null;
        }
        try {
            return SystemRole.valueOf(roleId.value());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static SystemRole from(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return null;
        }
        return from(RoleId.of(roleKey));
    }
}
