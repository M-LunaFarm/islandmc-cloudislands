package kr.lunaf.cloudislands.coreservice.permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;

public final class InMemoryIslandPermissionRuleRepository implements IslandPermissionRuleRepository {
    private final Map<UUID, Map<String, IslandPermissionRuleSnapshot>> rules = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, IslandPermissionOverrideSnapshot>> overrides = new ConcurrentHashMap<>();

    @Override
    public void putRoleKey(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        String normalizedRoleKey = kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
        rules.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(key(normalizedRoleKey, permission), new IslandPermissionRuleSnapshot(islandId, normalizedRoleKey, permission, allowed));
    }

    @Override
    public List<IslandPermissionRuleSnapshot> list(UUID islandId) {
        List<IslandPermissionRuleSnapshot> snapshots = new ArrayList<>(rules.getOrDefault(islandId, Map.of()).values());
        snapshots.sort(Comparator.comparing(IslandPermissionRuleSnapshot::effectiveRoleKey).thenComparing(rule -> rule.permission().name()));
        return List.copyOf(snapshots);
    }

    @Override
    public void putPlayerOverride(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed) {
        overrides.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(playerKey(playerUuid, permission), new IslandPermissionOverrideSnapshot(islandId, playerUuid, permission, allowed));
    }

    @Override
    public List<IslandPermissionOverrideSnapshot> listPlayerOverrides(UUID islandId) {
        List<IslandPermissionOverrideSnapshot> snapshots = new ArrayList<>(overrides.getOrDefault(islandId, Map.of()).values());
        snapshots.sort(Comparator.comparing((IslandPermissionOverrideSnapshot rule) -> rule.playerUuid().toString()).thenComparing(rule -> rule.permission().name()));
        return List.copyOf(snapshots);
    }

    private String key(String roleKey, IslandPermission permission) {
        return roleKey + ":" + permission.name();
    }

    private String playerKey(UUID playerUuid, IslandPermission permission) {
        return playerUuid + ":" + permission.name();
    }
}
