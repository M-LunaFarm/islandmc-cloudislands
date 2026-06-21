package kr.lunaf.cloudislands.coreservice.role;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.RoleDefinition;
import kr.lunaf.cloudislands.api.model.RoleId;

public interface IslandRoleRepository {
    IslandRoleSnapshot upsertKey(UUID islandId, String roleKey, int weight, String displayName);
    boolean resetKey(UUID islandId, String roleKey);
    List<IslandRoleSnapshot> list(UUID islandId);

    static List<IslandRoleSnapshot> mergeDefaults(UUID islandId, List<IslandRoleSnapshot> overrides) {
        java.util.Map<String, IslandRoleSnapshot> merged = new java.util.LinkedHashMap<>();
        for (RoleDefinition definition : RoleDefinition.defaultMemberRoles()) {
            String roleKey = definition.roleId().value();
            merged.put(roleKey, new IslandRoleSnapshot(islandId, roleKey, definition.weight(), definition.displayName()));
        }
        for (IslandRoleSnapshot override : overrides) {
            String roleKey = normalizeRoleKey(override.effectiveRoleKey());
            if (editableRoleKey(roleKey)) {
                merged.put(roleKey, override);
            }
        }
        return merged.values().stream()
            .sorted(java.util.Comparator.comparingInt(IslandRoleSnapshot::weight).thenComparing(IslandRoleSnapshot::effectiveRoleKey))
            .toList();
    }

    static boolean editableRoleKey(String roleKey) {
        String normalized = normalizeRoleKey(roleKey);
        if (normalized.isBlank()) {
            return false;
        }
        return RoleDefinition.editable(RoleId.of(normalized));
    }

    static String normalizeRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
