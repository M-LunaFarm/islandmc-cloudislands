package kr.lunaf.cloudislands.common.permission;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;

public final class PermissionResolver {
    private final CachedPermissionSet permissionSet;
    private final Map<UUID, IslandRole> cachedRoles;
    private final Map<UUID, Map<IslandPermission, Boolean>> playerOverrides;

    public PermissionResolver(CachedPermissionSet permissionSet, Map<UUID, IslandRole> cachedRoles) {
        this(permissionSet, cachedRoles, Map.of());
    }

    public PermissionResolver(CachedPermissionSet permissionSet, Map<UUID, IslandRole> cachedRoles, Map<UUID, Map<IslandPermission, Boolean>> playerOverrides) {
        this.permissionSet = permissionSet;
        this.cachedRoles = cachedRoles;
        this.playerOverrides = playerOverrides;
    }

    public PermissionResult check(UUID playerUuid, IslandPermission permission, boolean adminBypass) {
        if (adminBypass) {
            return PermissionResult.allow(IslandRole.OWNER);
        }
        IslandRole role = cachedRoles.getOrDefault(playerUuid, IslandRole.VISITOR);
        if (role == IslandRole.OWNER) {
            return PermissionResult.allow(IslandRole.OWNER);
        }
        Boolean override = playerOverrides.getOrDefault(playerUuid, Map.of()).get(permission);
        if (override != null) {
            return override ? PermissionResult.allow(role) : PermissionResult.deny("PLAYER_PERMISSION_OVERRIDE", role);
        }
        if (permissionSet.allowed(role, permission)) {
            return PermissionResult.allow(role);
        }
        return PermissionResult.deny("DEFAULT_DENY", role);
    }
}
