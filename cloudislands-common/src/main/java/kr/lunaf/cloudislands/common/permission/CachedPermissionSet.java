package kr.lunaf.cloudislands.common.permission;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

public final class CachedPermissionSet {
    private static final String OWNER_ROLE_KEY = "OWNER";
    private static final String CO_OWNER_ROLE_KEY = "CO_OWNER";
    private static final String BANNED_ROLE_KEY = "BANNED";
    private static final String VISITOR_ROLE_KEY = "VISITOR";

    private final Map<String, Map<IslandPermission, Boolean>> rules = new HashMap<>();

    public void putRoleKey(String roleKey, IslandPermission permission, boolean allowed) {
        rules.computeIfAbsent(normalizeRoleKey(roleKey), ignored -> new EnumMap<>(IslandPermission.class)).put(permission, allowed);
    }

    public boolean allowedRoleKey(String roleKey, IslandPermission permission) {
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (normalizedRoleKey.equals(OWNER_ROLE_KEY) || normalizedRoleKey.equals(CO_OWNER_ROLE_KEY)) {
            return true;
        }
        if (normalizedRoleKey.equals(BANNED_ROLE_KEY)) {
            return false;
        }
        return rules.getOrDefault(normalizedRoleKey, Map.of()).getOrDefault(permission, false);
    }

    @SuppressWarnings("deprecation")
    public void put(IslandRole role, IslandPermission permission, boolean allowed) {
        putRoleKey(role == null ? VISITOR_ROLE_KEY : role.name(), permission, allowed);
    }

    @SuppressWarnings("deprecation")
    public boolean allowed(IslandRole role, IslandPermission permission) {
        return allowedRoleKey(role == null ? VISITOR_ROLE_KEY : role.name(), permission);
    }

    private static String normalizeRoleKey(String roleKey) {
        return RoleId.normalize(roleKey, VISITOR_ROLE_KEY);
    }
}
