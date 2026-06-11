package kr.lunaf.cloudislands.coreservice.permission;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;

public interface IslandPermissionRuleRepository {
    void put(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed);
    List<IslandPermissionRuleSnapshot> list(UUID islandId);
}
