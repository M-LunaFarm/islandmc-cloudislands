package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.PermissionResolver;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;

public final class LocalIslandPermissionCache {
    private final Map<UUID, CachedIslandPermissions> islands = new ConcurrentHashMap<>();

    public void putRole(UUID islandId, UUID playerUuid, IslandRole role) {
        islands.computeIfAbsent(islandId, ignored -> empty()).roles().put(playerUuid, role);
    }

    public void putRule(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        islands.computeIfAbsent(islandId, ignored -> empty()).permissions().put(role, permission, allowed);
    }

    public void putFlag(UUID islandId, IslandFlag flag, String value) {
        islands.computeIfAbsent(islandId, ignored -> empty()).flags().put(flag, value == null ? "" : value);
    }

    public boolean allowed(UUID islandId, UUID playerUuid, IslandPermission permission, boolean adminBypass) {
        CachedIslandPermissions cached = islands.computeIfAbsent(islandId, ignored -> empty());
        return new PermissionResolver(cached.permissions(), cached.roles()).check(playerUuid, permission, adminBypass).allowed();
    }

    public boolean flagAllowed(UUID islandId, IslandFlag flag) {
        CachedIslandPermissions cached = islands.computeIfAbsent(islandId, ignored -> empty());
        String value = cached.flags().get(flag);
        return value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("allowed") || value.equalsIgnoreCase("enabled") || value.equalsIgnoreCase("on"));
    }

    public void invalidate(UUID islandId) {
        islands.remove(islandId);
    }

    public void invalidateAll() {
        islands.clear();
    }

    private CachedIslandPermissions empty() {
        return new CachedIslandPermissions(DefaultIslandPermissions.create(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private record CachedIslandPermissions(CachedPermissionSet permissions, Map<UUID, IslandRole> roles, Map<IslandFlag, String> flags) {}
}
