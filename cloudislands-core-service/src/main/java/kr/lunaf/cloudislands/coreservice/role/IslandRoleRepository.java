package kr.lunaf.cloudislands.coreservice.role;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;

public interface IslandRoleRepository {
    IslandRoleSnapshot upsert(UUID islandId, IslandRole role, int weight, String displayName);
    IslandRoleSnapshot upsertKey(UUID islandId, String roleKey, int weight, String displayName);
    boolean reset(UUID islandId, IslandRole role);
    boolean resetKey(UUID islandId, String roleKey);
    List<IslandRoleSnapshot> list(UUID islandId);

    static List<IslandRoleSnapshot> mergeDefaults(UUID islandId, List<IslandRoleSnapshot> overrides) {
        java.util.Map<String, IslandRoleSnapshot> merged = new java.util.LinkedHashMap<>();
        for (IslandRole role : IslandRole.values()) {
            if (role != IslandRole.OWNER && role.islandMemberRole()) {
                merged.put(role.name(), new IslandRoleSnapshot(islandId, role, role.ordinal(), role.name()));
            }
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
        return !normalized.equals(IslandRole.OWNER.name())
            && !normalized.equals(IslandRole.VISITOR.name())
            && !normalized.equals(IslandRole.BANNED.name());
    }

    static String normalizeRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
