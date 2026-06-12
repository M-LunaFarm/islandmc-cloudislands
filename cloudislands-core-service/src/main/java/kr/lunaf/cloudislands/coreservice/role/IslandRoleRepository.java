package kr.lunaf.cloudislands.coreservice.role;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;

public interface IslandRoleRepository {
    IslandRoleSnapshot upsert(UUID islandId, IslandRole role, int weight, String displayName);
    List<IslandRoleSnapshot> list(UUID islandId);

    static List<IslandRoleSnapshot> mergeDefaults(UUID islandId, List<IslandRoleSnapshot> overrides) {
        Map<IslandRole, IslandRoleSnapshot> merged = new java.util.EnumMap<>(IslandRole.class);
        for (IslandRole role : IslandRole.values()) {
            if (role != IslandRole.OWNER && role.islandMemberRole()) {
                merged.put(role, new IslandRoleSnapshot(islandId, role, role.ordinal(), role.name()));
            }
        }
        for (IslandRoleSnapshot override : overrides) {
            if (override.role() != IslandRole.OWNER && override.role().islandMemberRole()) {
                merged.put(override.role(), override);
            }
        }
        return merged.values().stream()
            .sorted(java.util.Comparator.comparingInt(IslandRoleSnapshot::weight).thenComparing(snapshot -> snapshot.role().name()))
            .toList();
    }
}
