package kr.lunaf.cloudislands.coreservice.repository;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public interface IslandMetadataRepository {
    List<IslandMemberSnapshot> members(UUID islandId);
    boolean isMember(UUID islandId, UUID playerUuid);
    void upsertMember(UUID islandId, UUID playerUuid, IslandRole role);
    void removeMember(UUID islandId, UUID playerUuid);
    IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid);
    List<IslandInviteSnapshot> pendingInvites(UUID targetUuid);
    boolean acceptInvite(UUID inviteId, UUID playerUuid);
    boolean declineInvite(UUID inviteId, UUID playerUuid);
    boolean isBanned(UUID islandId, UUID playerUuid);
    List<IslandBanSnapshot> bans(UUID islandId);
    void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason);
    void pardonVisitor(UUID islandId, UUID playerUuid);
    boolean isLocked(UUID islandId);
    void setLocked(UUID islandId, boolean locked);
    IslandFlagsSnapshot flags(UUID islandId);
    void setFlag(UUID islandId, IslandFlag flag, String value);
    List<IslandWarpSnapshot> warps(UUID islandId);
    void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy);
    void deleteWarp(UUID islandId, String name);
    void setPublicAccess(UUID islandId, boolean publicAccess);
    List<UUID> publicIslandIds(int limit);
}
