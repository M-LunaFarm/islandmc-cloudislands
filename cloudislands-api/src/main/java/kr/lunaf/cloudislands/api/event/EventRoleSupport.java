package kr.lunaf.cloudislands.api.event;

import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

@SuppressWarnings("deprecation")
final class EventRoleSupport {
    private EventRoleSupport() {
    }

    static String canonicalRoleKey(String roleKey, IslandRole legacyRole, String fallback) {
        String legacyFallback = legacyRole == null ? fallback : legacyRole.name();
        return RoleId.normalize(roleKey, legacyFallback);
    }

    static IslandRole legacyRole(String roleKey) {
        try {
            return IslandRole.valueOf(roleKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
