package kr.lunaf.cloudislands.common.permission;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;

public final class PermissionResolver {
    private final CachedPermissionSet permissionSet;
    private final Map<UUID, IslandRole> cachedRoles;

    public PermissionResolver(CachedPermissionSet permissionSet, Map<UUID, IslandRole> cachedRoles) {
        this.permissionSet = permissionSet;
        this.cachedRoles = cachedRoles;
    }

    public PermissionResult check(UUID playerUuid, IslandPermission permission, boolean adminBypass) {
        if (adminBypass) {
            return PermissionResult.allow(IslandRole.OWNER);
        }
        IslandRole role = cachedRoles.getOrDefault(playerUuid, IslandRole.VISITOR);
        if (role == IslandRole.OWNER) {
            return PermissionResult.allow(IslandRole.OWNER);
        }
        if (permissionSet.allowed(role, permission)) {
            return PermissionResult.allow(role);
        }
        return PermissionResult.deny("DEFAULT_DENY", role);
    }
}
