package kr.lunaf.cloudislands.coreservice.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;

public final class InMemoryIslandRoleRepository implements IslandRoleRepository {
    private final Map<UUID, Map<IslandRole, IslandRoleSnapshot>> roles = new ConcurrentHashMap<>();

    @Override
    public IslandRoleSnapshot upsert(UUID islandId, IslandRole role, int weight, String displayName) {
        IslandRoleSnapshot snapshot = new IslandRoleSnapshot(islandId, role, weight, displayName == null ? "" : displayName);
        roles.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(role, snapshot);
        return snapshot;
    }

    @Override
    public boolean reset(UUID islandId, IslandRole role) {
        Map<IslandRole, IslandRoleSnapshot> islandRoles = roles.get(islandId);
        return islandRoles != null && islandRoles.remove(role) != null;
    }

    @Override
    public List<IslandRoleSnapshot> list(UUID islandId) {
        List<IslandRoleSnapshot> snapshots = new ArrayList<>(roles.getOrDefault(islandId, Map.of()).values());
        return IslandRoleRepository.mergeDefaults(islandId, snapshots);
    }
}
