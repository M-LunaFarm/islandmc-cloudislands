package kr.lunaf.cloudislands.coreservice.role;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;

public interface IslandRoleRepository {
    IslandRoleSnapshot upsert(UUID islandId, IslandRole role, int weight, String displayName);
    List<IslandRoleSnapshot> list(UUID islandId);
}
