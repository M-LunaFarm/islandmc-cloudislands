package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.IslandPermissionSystemPolicy;
import kr.lunaf.cloudislands.common.permission.PermissionResolver;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;

public final class LocalIslandPermissionCache {
    private static final String OWNER_ROLE_KEY = "OWNER";
    private static final String BANNED_ROLE_KEY = "BANNED";
    private static final String VISITOR_ROLE_KEY = "VISITOR";

    private final Map<UUID, CachedIslandPermissions> islands = new ConcurrentHashMap<>();
    private final AtomicLong lookups = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();

    public void putRole(UUID islandId, UUID playerUuid, IslandRole role) {
        putRoleKey(islandId, playerUuid, role == null ? VISITOR_ROLE_KEY : role.name());
    }

    public void putRoleKey(UUID islandId, UUID playerUuid, String roleKey) {
        CachedIslandPermissions cached = islands.computeIfAbsent(islandId, ignored -> empty());
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (!normalizedRoleKey.isBlank()) {
            cached.roleCatalog().add(normalizedRoleKey);
        }
        cached.roleKeys().put(playerUuid, normalizedRoleKey);
    }

    public void putRule(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        putRuleKey(islandId, role.name(), permission, allowed);
    }

    public void putRuleKey(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        CachedIslandPermissions cached = islands.computeIfAbsent(islandId, ignored -> empty());
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (!normalizedRoleKey.isBlank()) {
            cached.roleCatalog().add(normalizedRoleKey);
        }
        cached.dynamicRules()
            .computeIfAbsent(normalizedRoleKey, ignored -> new ConcurrentHashMap<>())
            .put(permission, allowed);
        IslandRole role = roleOrNull(normalizedRoleKey);
        if (role != null) {
            cached.permissions().put(role, permission, allowed);
        }
    }

    public void putPlayerOverride(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed) {
        islands.computeIfAbsent(islandId, ignored -> empty())
            .overrides()
            .computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
            .put(permission, allowed);
    }

    public void putRoleDefinition(UUID islandId, String roleKey) {
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (!normalizedRoleKey.isBlank()) {
            islands.computeIfAbsent(islandId, ignored -> empty()).roleCatalog().add(normalizedRoleKey);
        }
    }

    public void putFlag(UUID islandId, IslandFlag flag, String value) {
        islands.computeIfAbsent(islandId, ignored -> empty()).flags().put(flag, value == null ? "" : value);
    }

    public boolean allowed(UUID islandId, UUID playerUuid, IslandPermission permission, boolean adminBypass) {
        CachedIslandPermissions cached = cached(islandId);
        if (adminBypass) {
            return true;
        }
        Boolean override = cached.overrides().getOrDefault(playerUuid, Map.of()).get(permission);
        if (override != null) {
            return override;
        }
        String roleKey = roleKey(islandId, playerUuid);
        if (roleKey.equals(OWNER_ROLE_KEY)) {
            return true;
        }
        if (roleKey.equals(BANNED_ROLE_KEY)) {
            return false;
        }
        Boolean dynamicRule = cached.dynamicRules().getOrDefault(roleKey, Map.of()).get(permission);
        if (dynamicRule != null) {
            return dynamicRule;
        }
        return PermissionResolver.fromRoleKeys(cached.permissions(), Map.of(playerUuid, roleKey), cached.overrides()).check(playerUuid, permission, adminBypass).allowed();
    }

    public IslandRole role(UUID islandId, UUID playerUuid) {
        IslandRole role = roleOrNull(roleKey(islandId, playerUuid));
        return role == null ? IslandRole.VISITOR : role;
    }

    public String roleKey(UUID islandId, UUID playerUuid) {
        CachedIslandPermissions cached = cached(islandId);
        return cached.roleKeys().getOrDefault(playerUuid, VISITOR_ROLE_KEY);
    }

    public java.util.List<String> roleCatalog(UUID islandId, boolean includeVisitor) {
        CachedIslandPermissions cached = cached(islandId);
        Set<String> values = new java.util.TreeSet<>();
        IslandPermissionSystemPolicy.baseRoleKeys().stream()
            .filter(roleKey -> !roleKey.equals(OWNER_ROLE_KEY) && !roleKey.equals(VISITOR_ROLE_KEY) && !roleKey.equals(BANNED_ROLE_KEY))
            .forEach(values::add);
        if (includeVisitor) {
            values.add(VISITOR_ROLE_KEY);
        }
        cached.roleCatalog().stream()
            .filter(roleKey -> includeVisitor || (!roleKey.equals(VISITOR_ROLE_KEY) && !roleKey.equals(BANNED_ROLE_KEY)))
            .filter(roleKey -> !roleKey.equals(OWNER_ROLE_KEY))
            .forEach(values::add);
        return java.util.List.copyOf(values);
    }

    public boolean flagAllowed(UUID islandId, IslandFlag flag) {
        CachedIslandPermissions cached = cached(islandId);
        String value = cached.flags().get(flag);
        return value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("allowed") || value.equalsIgnoreCase("enabled") || value.equalsIgnoreCase("on"));
    }

    public double hitRatio() {
        long total = lookups.get();
        return total <= 0L ? 1.0D : Math.min(1.0D, (double) hits.get() / total);
    }

    public long lookupCount() {
        return lookups.get();
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return Math.max(0L, lookups.get() - hits.get());
    }

    public int cachedIslandCount() {
        return islands.size();
    }

    public void invalidate(UUID islandId) {
        islands.remove(islandId);
    }

    public void invalidateAll() {
        islands.clear();
    }

    private CachedIslandPermissions empty() {
        return new CachedIslandPermissions(DefaultIslandPermissions.create(), new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private CachedIslandPermissions cached(UUID islandId) {
        lookups.incrementAndGet();
        CachedIslandPermissions existing = islands.get(islandId);
        if (existing != null) {
            hits.incrementAndGet();
            return existing;
        }
        return islands.computeIfAbsent(islandId, ignored -> empty());
    }

    private static String normalizeRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static IslandRole roleOrNull(String roleKey) {
        try {
            return IslandRole.valueOf(roleKey);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record CachedIslandPermissions(CachedPermissionSet permissions, Map<UUID, String> roleKeys, Set<String> roleCatalog, Map<String, Map<IslandPermission, Boolean>> dynamicRules, Map<IslandFlag, String> flags, Map<UUID, Map<IslandPermission, Boolean>> overrides) {}
}
