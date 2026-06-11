package kr.lunaf.cloudislands.common.permission;

import java.util.EnumMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;

public final class CachedPermissionSet {
    private final Map<IslandRole, Map<IslandPermission, Boolean>> rules = new EnumMap<>(IslandRole.class);

    public void put(IslandRole role, IslandPermission permission, boolean allowed) {
        rules.computeIfAbsent(role, ignored -> new EnumMap<>(IslandPermission.class)).put(permission, allowed);
    }

    public boolean allowed(IslandRole role, IslandPermission permission) {
        if (role == IslandRole.OWNER || role == IslandRole.CO_OWNER) {
            return true;
        }
        if (role == IslandRole.BANNED) {
            return false;
        }
        return rules.getOrDefault(role, Map.of()).getOrDefault(permission, false);
    }
}
