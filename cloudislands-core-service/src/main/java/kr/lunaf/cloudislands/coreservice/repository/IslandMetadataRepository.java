package kr.lunaf.cloudislands.coreservice.repository;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public interface IslandMetadataRepository {
    List<IslandMemberSnapshot> members(UUID islandId);
    void upsertMember(UUID islandId, UUID playerUuid, IslandRole role);
    void removeMember(UUID islandId, UUID playerUuid);
    IslandFlagsSnapshot flags(UUID islandId);
    void setFlag(UUID islandId, IslandFlag flag, String value);
    List<IslandWarpSnapshot> warps(UUID islandId);
    void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy);
    void deleteWarp(UUID islandId, String name);
    void setPublicAccess(UUID islandId, boolean publicAccess);
}
