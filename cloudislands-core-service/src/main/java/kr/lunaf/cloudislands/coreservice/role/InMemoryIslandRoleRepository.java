package kr.lunaf.cloudislands.coreservice.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;

public final class InMemoryIslandRoleRepository implements IslandRoleRepository {
    private final Map<UUID, Map<String, IslandRoleSnapshot>> roles = new ConcurrentHashMap<>();

    @Override
    public IslandRoleSnapshot upsert(UUID islandId, kr.lunaf.cloudislands.api.model.IslandRole role, int weight, String displayName) {
        return upsertKey(islandId, role.name(), weight, displayName);
    }

    @Override
    public IslandRoleSnapshot upsertKey(UUID islandId, String roleKey, int weight, String displayName) {
        String normalizedRoleKey = IslandRoleRepository.normalizeRoleKey(roleKey);
        IslandRoleSnapshot snapshot = new IslandRoleSnapshot(islandId, normalizedRoleKey, weight, displayName == null ? "" : displayName);
        roles.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(normalizedRoleKey, snapshot);
        return snapshot;
    }

    @Override
    public boolean reset(UUID islandId, kr.lunaf.cloudislands.api.model.IslandRole role) {
        return resetKey(islandId, role.name());
    }

    @Override
    public boolean resetKey(UUID islandId, String roleKey) {
        Map<String, IslandRoleSnapshot> islandRoles = roles.get(islandId);
        return islandRoles != null && islandRoles.remove(IslandRoleRepository.normalizeRoleKey(roleKey)) != null;
    }

    @Override
    public List<IslandRoleSnapshot> list(UUID islandId) {
        List<IslandRoleSnapshot> snapshots = new ArrayList<>(roles.getOrDefault(islandId, Map.of()).values());
        return IslandRoleRepository.mergeDefaults(islandId, snapshots);
    }
}
