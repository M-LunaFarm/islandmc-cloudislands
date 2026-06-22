package kr.lunaf.cloudislands.common.permission;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;

public final class PermissionResolver {
    private static final String OWNER_ROLE_KEY = "OWNER";
    private static final String BANNED_ROLE_KEY = "BANNED";
    private static final String VISITOR_ROLE_KEY = "VISITOR";

    private final CachedPermissionSet permissionSet;
    private final Map<UUID, String> cachedRoleKeys;
    private final Map<UUID, Map<IslandPermission, Boolean>> playerOverrides;

    public PermissionResolver(CachedPermissionSet permissionSet, Map<UUID, IslandRole> cachedRoles) {
        this(permissionSet, cachedRoles, Map.of());
    }

    public PermissionResolver(CachedPermissionSet permissionSet, Map<UUID, IslandRole> cachedRoles, Map<UUID, Map<IslandPermission, Boolean>> playerOverrides) {
        this(permissionSet, legacyRoleKeys(cachedRoles), playerOverrides, true);
    }

    private PermissionResolver(CachedPermissionSet permissionSet, Map<UUID, String> cachedRoleKeys, Map<UUID, Map<IslandPermission, Boolean>> playerOverrides, boolean roleKeyInput) {
        this.permissionSet = permissionSet;
        this.cachedRoleKeys = cachedRoleKeys;
        this.playerOverrides = playerOverrides;
    }

    public static PermissionResolver fromRoleKeys(CachedPermissionSet permissionSet, Map<UUID, String> cachedRoleKeys) {
        return fromRoleKeys(permissionSet, cachedRoleKeys, Map.of());
    }

    public static PermissionResolver fromRoleKeys(CachedPermissionSet permissionSet, Map<UUID, String> cachedRoleKeys, Map<UUID, Map<IslandPermission, Boolean>> playerOverrides) {
        return new PermissionResolver(permissionSet, normalizeRoleKeys(cachedRoleKeys), playerOverrides, true);
    }

    public PermissionResult check(UUID playerUuid, IslandPermission permission, boolean adminBypass) {
        if (adminBypass) {
            return PermissionResult.allow(IslandRole.OWNER);
        }
        String roleKey = cachedRoleKeys.getOrDefault(playerUuid, VISITOR_ROLE_KEY);
        if (roleKey.equals(OWNER_ROLE_KEY)) {
            return PermissionResult.allow(IslandRole.OWNER);
        }
        IslandRole effectiveRole = legacyRole(roleKey);
        Boolean override = playerOverrides.getOrDefault(playerUuid, Map.of()).get(permission);
        if (override != null) {
            return override ? PermissionResult.allow(effectiveRole) : PermissionResult.deny("PLAYER_PERMISSION_OVERRIDE", effectiveRole);
        }
        if (roleKey.equals(BANNED_ROLE_KEY)) {
            return PermissionResult.deny("DEFAULT_DENY", IslandRole.BANNED);
        }
        if (permissionSet.allowed(effectiveRole, permission)) {
            return PermissionResult.allow(effectiveRole);
        }
        return PermissionResult.deny("DEFAULT_DENY", effectiveRole);
    }

    private static Map<UUID, String> legacyRoleKeys(Map<UUID, IslandRole> cachedRoles) {
        return cachedRoles.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue() == null ? VISITOR_ROLE_KEY : entry.getValue().name()
            ));
    }

    private static Map<UUID, String> normalizeRoleKeys(Map<UUID, String> cachedRoleKeys) {
        return cachedRoleKeys.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> normalizeRoleKey(entry.getValue())
            ));
    }

    private static String normalizeRoleKey(String roleKey) {
        String value = roleKey == null ? "" : roleKey.trim();
        if (value.isBlank()) {
            return VISITOR_ROLE_KEY;
        }
        return value.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static IslandRole legacyRole(String roleKey) {
        try {
            return IslandRole.valueOf(roleKey);
        } catch (IllegalArgumentException exception) {
            return IslandRole.VISITOR;
        }
    }
}
