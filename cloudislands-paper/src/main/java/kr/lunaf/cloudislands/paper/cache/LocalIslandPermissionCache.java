package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.PermissionResolver;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;

public final class LocalIslandPermissionCache {
    private final Map<UUID, CachedIslandPermissions> islands = new ConcurrentHashMap<>();

    public void putRole(UUID islandId, UUID playerUuid, IslandRole role) {
        islands.computeIfAbsent(islandId, ignored -> new CachedIslandPermissions(DefaultIslandPermissions.create(), new ConcurrentHashMap<>())).roles().put(playerUuid, role);
    }

    public void putRule(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        islands.computeIfAbsent(islandId, ignored -> new CachedIslandPermissions(DefaultIslandPermissions.create(), new ConcurrentHashMap<>())).permissions().put(role, permission, allowed);
    }

    public boolean allowed(UUID islandId, UUID playerUuid, IslandPermission permission, boolean adminBypass) {
        CachedIslandPermissions cached = islands.computeIfAbsent(islandId, ignored -> new CachedIslandPermissions(DefaultIslandPermissions.create(), new ConcurrentHashMap<>()));
        return new PermissionResolver(cached.permissions(), cached.roles()).check(playerUuid, permission, adminBypass).allowed();
    }

    public void invalidate(UUID islandId) {
        islands.remove(islandId);
    }

    public void invalidateAll() {
        islands.clear();
    }

    private record CachedIslandPermissions(CachedPermissionSet permissions, Map<UUID, IslandRole> roles) {}
}
