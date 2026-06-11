package kr.lunaf.cloudislands.coreservice.permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;

public final class InMemoryIslandPermissionRuleRepository implements IslandPermissionRuleRepository {
    private final Map<UUID, Map<String, IslandPermissionRuleSnapshot>> rules = new ConcurrentHashMap<>();

    @Override
    public void put(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        rules.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(key(role, permission), new IslandPermissionRuleSnapshot(islandId, role, permission, allowed));
    }

    @Override
    public List<IslandPermissionRuleSnapshot> list(UUID islandId) {
        List<IslandPermissionRuleSnapshot> snapshots = new ArrayList<>(rules.getOrDefault(islandId, Map.of()).values());
        snapshots.sort(Comparator.comparing((IslandPermissionRuleSnapshot rule) -> rule.role().name()).thenComparing(rule -> rule.permission().name()));
        return List.copyOf(snapshots);
    }

    private String key(IslandRole role, IslandPermission permission) {
        return role.name() + ":" + permission.name();
    }
}
