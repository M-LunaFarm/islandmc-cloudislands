package kr.lunaf.cloudislands.api.model;

@SuppressWarnings("deprecation")
final class LegacyRoleSupport {
    private LegacyRoleSupport() {
    }

    static String canonicalRoleKey(String roleKey, IslandRole legacyRole) {
        if (roleKey != null && !roleKey.isBlank()) {
            return RoleId.of(roleKey).value();
        }
        if (legacyRole != null) {
            return RoleId.of(legacyRole.name()).value();
        }
        throw new IllegalArgumentException("role id is required");
    }

    static RoleId canonicalRoleId(RoleId roleId, IslandRole legacyRole, String fallback) {
        return roleId == null ? RoleId.of(legacyRole == null ? "" : legacyRole.name(), fallback) : RoleId.of(roleId.value());
    }

    static IslandRole legacyRole(String roleKey) {
        try {
            return IslandRole.valueOf(RoleId.of(roleKey).value());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
