package kr.lunaf.cloudislands.coreservice.permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;

public interface IslandPermissionRuleRepository {
    void put(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed);
    List<IslandPermissionRuleSnapshot> list(UUID islandId);
    void putPlayerOverride(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed);
    List<IslandPermissionOverrideSnapshot> listPlayerOverrides(UUID islandId);

    default Optional<Boolean> playerOverride(UUID islandId, UUID playerUuid, IslandPermission permission) {
        return listPlayerOverrides(islandId).stream()
            .filter(rule -> rule.playerUuid().equals(playerUuid) && rule.permission() == permission)
            .map(IslandPermissionOverrideSnapshot::allowed)
            .findFirst();
    }

    default boolean allowed(UUID islandId, UUID playerUuid, IslandRole role, IslandPermission permission) {
        Optional<Boolean> override = playerOverride(islandId, playerUuid, permission);
        if (override.isPresent()) {
            return override.get();
        }
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        for (IslandPermissionRuleSnapshot rule : list(islandId)) {
            permissions.put(rule.role(), rule.permission(), rule.allowed());
        }
        return permissions.allowed(role, permission);
    }
}
